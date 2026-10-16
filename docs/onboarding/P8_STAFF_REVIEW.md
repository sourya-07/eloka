# P8: staff review API (design note)

> Status: **built** (2026-10-16): S1–S13, S21, staff sign-in, 8 integration scenarios green (`StaffReviewIntegrationTest`). Phase P8 of [`../BACKEND_PLAN.md`](../BACKEND_PLAN.md). Contract: [API_SPECIFICATION.md](./API_SPECIFICATION.md) §4 (S1–S13, S21), state machine [ONBOARDING_STATE_MACHINE.md](./ONBOARDING_STATE_MACHINE.md) T4–T9.
> Replaces the Go-Live Admin tool (today's steps 7–10 and 12) and the email-to-CS + CRM upload of step 13 ([ELOKA_ONBOARDING_JOURNEY.md](./ELOKA_ONBOARDING_JOURNEY.md) §2).

**Done when:** a reviewer claims a submitted application, verifies documents, decides flagged verifications, requests a correction (the applicant fixes and resubmits) or recommends approval, and a **different** approver approves (or rejects). Every staff action is audited. Provisioning after approval is P9; the staff screens are FE-R2.

## 1. Staff identity

| | Decision |
|---|---|
| Production login | **Google sign-in** (Workspace accounts, ADR-0005 / BACKEND_PLAN): the browser gets a Google ID token; `POST /v1/admin/auth/google` verifies it (signature via Google's JWKS, issuer, audience = `ELOKA_STAFF_GOOGLE_CLIENT_ID`, `email_verified`, hosted domain = `ELOKA_STAFF_GOOGLE_DOMAIN`) and opens a `STAFF` session. Without a client id the endpoint answers `409 PROVIDER_NOT_CONFIGURED`; with an empty domain it refuses everyone (and production will not start). Every refused sign-in is audited `STAFF_LOGIN_REFUSED` with a reason code (`TOKEN_OR_DOMAIN`, `UNKNOWN_ACCOUNT`, `ACCOUNT_DISABLED`), never the email |
| MFA | Enforced by Google Workspace 2-step verification (ID tokens carry no `amr`). **ASSUMPTION** recorded as OQ-S6 |
| Local / test login | `POST /v1/admin/auth/otp` + `/otp/verify` with OTP `123456` for seeded staff mobiles (`9000000006` reviewer, `9000000007` approver, `9000000008` admin). The OTP route exists only in `local`/`test` (`UnsafeInProduction`) |
| Accounts | `staff_user` (email unique, optional mobile for local OTP, ACTIVE/DISABLED) + `staff_role_grant`. Production accounts are created with a one-shot `prod,staff-bootstrap` command (like the first tenant). Unknown or disabled emails cannot log in |
| Sessions | Same opaque tokens, session kind `STAFF`, valid only on `/v1/admin/**`; permissions are loaded into the `Caller` on every request, so a revoked role takes effect immediately |

Roles → permissions (code, `StaffRole`):

| Role | Permissions |
|---|---|
| `REVIEWER` | `onboarding.read`, `onboarding.review`, `onboarding.hold` |
| `APPROVER` | `onboarding.read`, `onboarding.approve`, `onboarding.reject`, `onboarding.hold`, `pii.reveal` |
| `AUDITOR` | `onboarding.read`, `audit.read` |
| `ADMIN` | all of the above, plus `onboarding.reassign` (take a claimed case back from its reviewer) |

## 2. Endpoints

S1 list (filters `status`, `assigned_to=me`, `q` = company name / `onb_` id / exact PAN via blind index; cursor paging) · S2 case (snapshot, masked business profile, verifications, documents, review history, flags) · S3 claim (T4) · S4 release · S5/S6 verify / reject a document · S7 decide a `MANUAL_REVIEW` verification · S8 corrections (T5) · S9 recommendation (maker) · S10 approve (T7, checker ≠ maker) · S11 reject (T8) · S12 hold / release hold (T9) · S13 PII reveal (one value, one response, audited `PII_REVEALED` with purpose) · S21 audit trail of an application. Plus `GET …/documents/{doc}/download` for reviewers and approvers (audited; auditors cannot open raw KYC files, which show the full PAN and account number). Each refresh endpoint rotates only its own session kind (`/v1/admin/auth/refresh` staff, `/v1/onboarding/session/refresh` applicants, the EPS app users). All side-effect POSTs take `Idempotency-Key`; writes to a case lock the application row. Every case response carries the case's `ETag`; every action except claim (made from the queue) needs `If-Match` with it: `428` without, `412 PRECONDITION_FAILED` when the case changed since the staff member opened it. Reason codes are at most 40 characters and `due_in_days` is 1–90 (else `422`). S21 returns the latest 500 events, oldest first.

## 3. Rules

- **Who may act:** review actions (S5–S9, S4) need the case claimed **by the caller**; an admin (`onboarding.reassign`) may release anyone's claimed case with a reason, e.g. when the reviewer left. Approve/reject need the approver permission and work on a claimed case; nobody may approve a case they claimed or recommended at any point, even after handing it over (`403 MAKER_CHECKER_VIOLATION`, E30).
- **Approve guard (T7):** a recommendation to approve exists (by someone else); every current document is `VERIFIED`; no verification of a current step is left in `MANUAL_REVIEW`. Otherwise `409 APPROVAL_BLOCKED` with the list of reasons.
- **Corrections (T5/T6):** the flagged steps go `LOCKED → REOPENED` with the reason code and message the applicant sees; the application becomes `CORRECTION_REQUIRED` with a due date (default 15 days, OQ-B9). When a reopened step is completed with changed data, the locked steps that depend on it reopen too, so a changed agreement is signed again. Completed again on the same data (same PAN hash, same program), only the declaration and the agreement reopen; paid checks such as the bank account stay locked. A reviewer who reopens `AGREEMENT_ESIGN` itself gets a new signature even if the content is unchanged. The applicant resubmits with A10 (or automatically after re-signing). The message is at most 1000 characters. A correction expires on its due date only (not the 30-day idle window, however long the review took); the applicant's `expires_at` shows that date.
- **Withdraw after claim** stays refused (E28): staff reject with reason `WITHDRAWN_BY_APPLICANT`.
- **Reject (T8)** releases the sub-domain (same path as withdraw/expiry). The applicant message and the internal note are both kept (`review_action.applicant_message`, V28, and `note`).
- **Audit:** every staff call records actor = staff id, action, target = application; PII reveals record the field and the id of the review-history entry that holds the free-text purpose (the audit log keeps ids and codes only), never the value.

## 4. Data

V26: `staff_user`, `staff_role_grant`; `review_action` (maker/checker history: claim, release, recommendation, approve, reject, hold, correction, document and verification decisions); `correction_request` (step keys, reason, message, due); `onboarding_application.assigned_staff_id`, `correction_due_at`; index on `audit_event (target_type, target_id, occurred_at)`.

## 5. Tests

Integration: reviewer claim → verify documents → decide flagged bank → recommend → same reviewer cannot approve → approver approves (`APPROVED`); correction loop (reopened step edited, agreement re-signed, resubmitted); reject releases the sub-domain; hold and release; PII reveal needs the permission and is audited; unclaimed / other reviewer's case refused; staff tokens refused on `/v1/onboarding`, applicant tokens refused on `/v1/admin`; Google login with a test verifier (wrong domain refused, unknown email refused).

## 6. Self-review (score 8/10)

Strong: follows the approved state machine and permission table, maker-checker enforced in one place, Google login behind a port with a local stand-in, no new UI assumptions. Gaps fixed in this note: production staff accounts (bootstrap command), immediate effect of revoked roles (permissions read per request), re-signing after corrections (locked dependents reopen). Remaining risks: MFA relies on Workspace policy (OQ-S1); SLA timers and escalation (OQ-O5) are not built; S14–S20 belong to P9.

## 7. What changed while building

- Review lives in the `onboarding` module (only the module that owns the application may change its status); the empty `review` placeholder module was removed.
- `PII_REVEAL` is on the `APPROVER` role for now; OQ-S2 assumes a separate compliance role, which is one line in `StaffRole` once decided.
- A correction invalidates earlier recommendations: after resubmission a new recommendation is needed before approval.
- After a correction the agreement is re-signed only when its content changed; an unchanged signed agreement completes the step again by itself.
- A rejected document no longer counts as uploaded for the documents step.
- Overdue corrections expire through the same hourly job as idle applications.
- Review fixes (2026-10-16, V27): corrections no longer expire on the idle window; `review_action.target_ref` (255) and `onboarding_step.reviewer_message` (1000) fit a several-step correction and its full message; a reviewer-reopened agreement is signed again; an unchanged corrected step keeps the paid checks built on it. Step summaries are masked, so "unchanged" is decided by the real input (PAN hash), never by comparing summaries.
- Review fixes (2026-10-16, items 6–12): admin force-release (`onboarding.reassign`); maker-checker covers anyone who ever claimed or recommended the case; downloads need review or approve; staff OTP gives unknown numbers a silent challenge (same rate limit and answers); refresh tokens work only on their own endpoint; the PII purpose left the audit log; refused Google sign-ins are audited and an empty Google domain refuses everyone.
- Review fixes (2026-10-16, items 13–23): a correction must reopen at least one step; reason code, due-days and message limits give `422` instead of a database error; reject keeps both texts (V28); staff actions need `If-Match`; an accepted verification drops its failure code; the staff bootstrap keeps name and roles when they are not given (so `ELOKA_STAFF_ACTIVE=false` alone disables an account); audit and queue times are read as UTC whatever the JVM zone; the audit trail filters by target type (uses its index) and keeps the newest events; the case view loads documents and verifications once.
- Staff sessions store the staff id (not the email) in the session row; the email comes from the account on each request.
- Staff accounts in production: `prod,staff-bootstrap` profile (`ELOKA_STAFF_EMAIL`, `ELOKA_STAFF_NAME`, `ELOKA_STAFF_ROLES`, `ELOKA_STAFF_ACTIVE`), see the runbook.


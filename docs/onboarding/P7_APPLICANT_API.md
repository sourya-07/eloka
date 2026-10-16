# P7: Local Admin applicant API (design note)

> Status: **built** (2026-10-16): all of A1–A30 plus re-auth, 11 integration scenarios green (`OnboardingApplicantIntegrationTest`). Phase P7 of [`../BACKEND_PLAN.md`](../BACKEND_PLAN.md). Contract source: [API_SPECIFICATION.md](./API_SPECIFICATION.md) §3 (A1–A30), [ONBOARDING_STATE_MACHINE.md](./ONBOARDING_STATE_MACHINE.md), [DATABASE_DESIGN.md](./DATABASE_DESIGN.md).
> Replaces today's steps 1–6 (Eko Connect sign-up) and the applicant half of steps 7–12 (Go-Live tool) of the current process ([ELOKA_ONBOARDING_JOURNEY.md](./ELOKA_ONBOARDING_JOURNEY.md) §2).

**Done when:** an applicant goes from mobile OTP to `SUBMITTED` through the API alone, with every rule below covered by an integration test. Staff review is P8, provisioning P9, the UI is FE-R2.

## 1. Scope

In: A1–A30 (identity, application snapshot, step draft/submit/skip, submit, withdraw, PAN / email / KYC / bank verifications, reference lists, documents, sub-domain availability, agreement + e-sign, timeline), the application and step state machines up to `SUBMITTED` (plus `WITHDRAWN`, `EXPIRED`), `ETag` / `If-Match`, `Idempotency-Key`, problem+json errors, the expiry job.

Out (later phases): staff APIs S1–S22 (P8), provisioning and activation (P9), leads and invites (A1 accepts `invite_token` but ignores it until P8), webhooks W1–W5 (no real providers yet), reminders by SMS/email (logged only), the UI (FE-R2).

## 2. Deviations from the blueprint (and why)

| Blueprint | P7 | Why |
|---|---|---|
| `__Host-onb_session` cookie via a BFF + CSRF token | Bearer tokens, session kind `LA_APPLICANT`, valid only on `/v1/onboarding/**` | ADR-0005. Same token machinery as the agents; no cookies, so no CSRF surface |
| Step-up `X-Reauth-Token` for bank, e-sign, withdraw | Same header. Issued by `POST /v1/onboarding/reauth` + `/reauth/verify` (OTP to the verified mobile), valid 5 minutes, single purpose | Keeps the blueprint rule with the existing OTP service |
| Presigned upload URL (A21) + complete (A22) | A21 is a **multipart upload** to the server; it returns the document already `PENDING_REVIEW`. A22 is kept as a no-op that returns the current status (clients written for the two-step flow still work) | There is no object store yet; the existing `documents` module encrypts at rest on disk. "Scanning" = content sniffing (magic bytes) + size limit, as for agents |
| Async providers + webhooks | Providers are ports with **local fakes**; outside `local`/`test` they answer `409 PROVIDER_NOT_CONFIGURED` (same as the Release 1 DigiLocker / e-sign ports) and the step stays retryable. A17 (bank) answers synchronously `200` instead of `202` | No provider contracts yet |
| A24 `302` to a presigned URL; A27 `preview_url` presigned | A24 streams the file; `preview_url` is `GET …/agreements/{id}/pdf` (session-protected) | No object store yet |
| `available_actions`, reminders, CRM sync | `available_actions` computed from the state machine; reminders and CRM are logged events only | Notifications and CRM are not integrated yet |
| Applicant platform | Applicants belong to no tenant. Their OTP challenges and sessions use the reserved platform org id `0` (no foreign key exists on those tables) | A tenant only exists after P9 provisioning |

## 3. Modules

| Module | Owns | Public API used by others |
|---|---|---|
| `onboarding` | `applicant`, `onboarding_application`, `onboarding_step`, `business_profile`, `consent_record`, `application_document`, `application_event`, `idempotency_record`; the state machines; the `/v1/onboarding` controllers | `OnboardingApi` (P8 review reads/moves applications) |
| `verification` | `verification` (PAN, email, KYC, bank attempts), name matching | `VerificationApi` |
| `whitelabel` | `domain_reservation`, `branding_draft`, `reserved_label` | `WhiteLabelApi` (reserve / release / availability) |
| `agreement` | `agreement` (versions, rendered PDF in `documents`), `esign_request` | `AgreementApi` |
| `integration` (exists) | ports + fakes: `PanVerificationProvider`, `BankAccountVerifier` (new), `DigiLockerProvider`, `EsignProvider` (exist) | — |

Controllers live in `onboarding/internal` (package-private, next to the services) and call the other modules through their APIs. Dependencies point one way: `onboarding` → `verification`, `whitelabel`, `agreement`, `documents`, `identity`, `reference`; `whitelabel` → `tenant`. `ModularityTests` keeps the boundaries. `shared.persistence.PublicIds` makes the `onb_…`, `doc_…`, `agr_…` ids.

## 4. State machines

Application: `IN_PROGRESS → SUBMITTED` (T3, auto on agreement `SIGNED` or A10), `→ WITHDRAWN` (T16, before a reviewer claims it), `→ EXPIRED` (T15, job). Every change goes through one method `ApplicationStateMachine.apply(application, event, actor)`: row lock (`SELECT … FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`), `If-Match` version check (`412` + snapshot), allowed `(from, event)` pair (else `409 INVALID_STATE_TRANSITION`), guard, `version + 1`, audit + `application_event` in the same transaction.

Steps (13, keys and prerequisites as in the state machine doc §4.1): `NOT_STARTED`, `IN_PROGRESS`, `PENDING_EXTERNAL`, `COMPLETED`, `FAILED`, `BLOCKED`, `SKIPPED`, `LOCKED`, `REOPENED`, `NOT_APPLICABLE`. Dependency invalidation per §4.3 (PAN change → business details, bank, agreement; business details change → agreement; KYC change → live photo, agreement). A signed agreement is never edited: a change marks it `SUPERSEDED`.

**Step policy** (configuration, `eloka.onboarding.step-policy` in `application.yaml`): per entity type which steps are required / optional / not applicable, which document types are required, max attempts per step. Defaults follow the working assumptions in [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md): live photo on (OQ-P4), bank mandatory (OQ-P6), logo/colours optional (OQ-P8), entity PAN with holder types `C` (company) for Pvt Ltd, `F` for LLP/partnership, `P` for sole proprietorship (OQ-B5), one active application per PAN (OQ-B6), 30 days idle expiry (OQ-B9). **Required document list (OQ-P5) has no business answer**: the default is one `ADDRESS_PROOF` for everyone plus `INCORPORATION_CERTIFICATE` (Pvt Ltd, LLP) or `PARTNERSHIP_DEED` (partnership); change it in config, not code.

## 5. Cross-cutting rules

- **Concurrency:** snapshot responses carry `ETag: "v<version>"`; writes need `If-Match` (`428 PRECONDITION_REQUIRED` when missing, `412` + current snapshot when stale).
- **Idempotency:** `Idempotency-Key` (UUID) required on every side-effect POST marked 🔁 in the spec. Stored per applicant with a SHA-256 of method + path + body and the response status/body for 24 h. Same key + same request → stored response; same key + different request → `422 IDEMPOTENCY_KEY_REUSED`; missing → `400`.
- **Errors:** problem+json with `code`, plus `invalid_params` for validation and `current` (snapshot) for `412` / `409`.
- **No existence oracle:** A1 answers the same for known and unknown mobiles; out-of-scope ids are `404`.
- **Personal data:** mobile, email, PAN, account number stored `_enc` + `_hash` + `_masked` (existing `FieldCipher` / `BlindIndex`); the Aadhaar number is never stored (only DigiLocker's masked value and name). Drafts never hold PAN, account number or OTPs.
- **Rate limits:** OTP limits from the identity module; verification attempt caps per step (`max_attempts`, then `BLOCKED`); per-route request-rate limits are not in P7 (tracked for the edge).
- **Audit:** every transition and verification attempt is an `audit_event` (ids and codes only).

## 6. Fakes (local / test only)

| Port | Fake behaviour (deterministic, so tests and demos can hit every branch) |
|---|---|
| PAN (by its four digits) | `0000` → `FAILED`; `9999` → `PENDING_EXTERNAL` on the first call, valid on the next; anything else → valid, name `DEMO ENTERPRISE PRIVATE LIMITED`, holder type = 4th character (so `ABCCD1234E` is a company PAN) |
| Bank | Account ending `0000` → `FAILED`; ending `1111` → another holder name (`MANUAL_REVIEW`, step completed with `name_matches: false`); else beneficiary = the PAN name |
| DigiLocker | Existing fake page (`/v1/dev/digilocker`); result name = the signatory name, Aadhaar `XXXX-XXXX-1234` |
| e-sign | Existing fake; signed as soon as A29 asks the provider |

Fixed local creds: OTP `123456` for login and re-auth (identity local profile), any 10-digit mobile. A walkthrough with `curl` is in [`../LOCAL_DEVELOPMENT.md`](../LOCAL_DEVELOPMENT.md).

## 7. Tests

Integration (`OnboardingApplicantIntegrationTest` and friends): full happy path OTP → SUBMITTED; resume returns the same application; `412` on a stale `If-Match`; idempotent replay and key reuse; PAN duplicate across applications (`409`); prerequisite order (`409 PREREQUISITE_INCOMPLETE`); dependency invalidation (PAN change resets the agreement); withdraw needs re-auth; sub-domain race (second reservation `409 SUBDOMAIN_TAKEN`); expiry job; agent tokens refused on `/v1/onboarding` and applicant tokens refused on EPS calls. Unit tests for the state machines and step policy. `ModularityTests` for the new modules.

## 8. Self-review (score 8/10, before building)

Strong: follows the approved contract and state machine, reuses identity/documents/integration instead of new infrastructure, every deviation is written down. Gaps fixed in this note: platform org id for applicants (no tenant yet), re-auth endpoint (the spec only named the header), A22 kept for compatibility, missing-`If-Match` behaviour (`428`). Remaining risks: the required document list and PAN holder types are working assumptions (OQ-P5, OQ-B5); fakes mean the provider error paths are exercised only through the fake rules above.

## 9. What changed while building

- Re-submitting a step with an **identical** result no longer resets later steps; resets cascade (PAN → business details → declaration → agreement).
- `PROGRAM_SELECTION` also resets `BUSINESS_PAN` and `DOCUMENTS` when it changes (the entity type decides the PAN holder types and the document list).
- `PROGRAM_SELECTION` needs `CONSENT` first (no business data before consent).
- Drafts accept only each step's allowed fields; anything else (PAN, account numbers, mobiles) is refused with `422`.
- A withdrawn, expired or rejected application releases its sub-domain in the same transaction.
- Fake e-sign document ids are now unique per session (one provider document per agreement version).


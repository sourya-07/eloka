# ELOKA Local Admin Onboarding — Implementation Plan

> Status: **DRAFT for architecture review** · **Revised 14 October 2026:** Java 25 + Spring Boot 4, MySQL 8.4 · Phase 0 complete when this document set is reviewed and the blocking open questions are answered.
> Nothing in this plan has been started. No code, packages or migrations exist yet.

---

## 1. Ordering principles (why this differs from the brief's example sequence)

The brief suggested putting notifications + audit at Phase 16 and security + observability at Phase 17. This plan moves **audit, idempotency, the outbox, encryption and observability into Phase 1**. Retro-fitting them later means rewriting every module that has already been written without them. Other changes:

- **The state machine ships with the onboarding core** (Phase 3), not separately. Every later step depends on it.
- **Mobile OTP moves before business steps** (Phase 2): every applicant call needs a session.
- **Phases follow the journey order** so that a demo-able vertical slice exists early (start → PAN → submit by Phase 7).
- **Provisioning comes after review**, and both are gated on EPS contracts (OQ-T2).
- **Frontend work** runs as a parallel track (FE-x) after each backend phase exposes its API. The frontend is **not** changed in Phase 0.

Phase gates: a phase starts only when its **blocking open questions** (listed per phase) are answered.

---

## 2. Phases

### Phase 0 — Requirements and architecture ✅ (this document set)

| Item | Detail |
|---|---|
| Objective | Approved blueprint |
| Deliverables | `docs/ARCHITECTURE.md`, `ELOKA_ONBOARDING_JOURNEY.md`, `ONBOARDING_STATE_MACHINE.md`, `FRONTEND_BACKEND_MAPPING.md`, `DATABASE_DESIGN.md`, `API_SPECIFICATION.md`, `INTEGRATIONS.md`, `SECURITY.md`, `IMPLEMENTATION_PLAN.md`, `OPEN_QUESTIONS.md` |
| Definition of done | Architecture review sign-off (engineering, product, compliance, EPS team). Blocking OQs for Phases 1–3 answered: **T5, T6, T7, P2, B2, B4, C5** (T1 closed 14 October 2026: MySQL) |

### Phase 1 — Backend foundation

| Item | Detail |
|---|---|
| Objective | Production-shaped skeleton that every module builds on |
| Modules | Repo + Gradle build + Spring Boot 4 / Spring Modulith app (`api` and `worker` Spring profiles), config, `crypto` (Tink + KMS envelope, HMAC, masking), `audit` (append-only, hash chain), outbox (Spring Modulith event publication registry) + JobRunr, `idempotency` filter, problem+json errors (`ProblemDetail`), correlation ids (MDC), Logback JSON with masking allow-list, Micrometer + OpenTelemetry, Actuator health / readiness, storage port + MinIO locally |
| APIs | `/actuator/health/liveness`, `/actuator/health/readiness`, OpenAPI at `/docs` (springdoc, internal) |
| DB | MySQL 8.4 database + Flyway (one DDL change per file); tables `audit_event`, `outbox_event`, `event_publication`, JobRunr tables, `idempotency_key`, `data_key`, `external_call_log`, `webhook_event`; MySQL users and grants (`onb_api`, `onb_worker`, `onb_migrate`, `onb_readonly`); Hibernate tenant-scope resolver (default deny) |
| Dependencies | OQ-T6 (hosting), OQ-T7 (object storage + KMS); OQ-T1 closed (MySQL) |
| Testing | Unit (crypto, masking, redaction), integration with Testcontainers MySQL (outbox atomicity, idempotency replay, scope default-deny), Spring Modulith `verify()` + ArchUnit boundary rules, log-scrubbing tests |
| Done when | CI green (Gradle build, tests, CodeQL, Sonar, gitleaks, OWASP Dependency-Check, Trivy); deployable to dev; a sample endpoint proves audit + outbox in one transaction |

### Phase 2 — Identity (applicant OTP and sessions; staff SSO skeleton)

| Item | Detail |
|---|---|
| Objective | Applicants can authenticate. Staff identities and RBAC exist. |
| Modules | `identity`, `notifications` (SMS via EPS adapter), BFF session route in the onboarding UI (FE-2) |
| APIs | A1–A4; staff `GET /v1/admin/me` |
| DB | `applicant`, `otp_challenge`, `applicant_session`, `staff_user`, `role`, `permission`, `notification` |
| Dependencies | OQ-I1 (OTP owner: EPS or this service), IdP client (OQ-T8 / IT) |
| Testing | OTP expiry, attempt limits, lockouts, enumeration-uniformity tests, session rotation / revocation, CSRF, rate limits |
| Done when | E2E: request OTP → verify → session cookie → `GET /applications/current` returns 404 (none yet); staff login with MFA claim enforced |

### Phase 3 — Lead and onboarding core + state machine

| Item | Detail |
|---|---|
| Objective | Application aggregate with a full state machine, step policy, resume, expiry |
| Modules | `onboarding` (aggregate, `transition()`, `step_policy`, invalidation rules, scheduler for reminders / expiry), `lead` (CRUD + invites) |
| APIs | A5–A11 (generic step save / submit / skip), S19–S20, S22 |
| Steps live | `MOBILE_VERIFICATION` (auto), `CONSENT`, `PROGRAM_SELECTION`, `BUSINESS_DETAILS` (without the name match), `DECLARATION` (stub) |
| DB | `lead`, `onboarding_application`, `onboarding_step`, `step_policy`, `business_profile` |
| Dependencies | OQ-B1, OQ-B3, OQ-B4, OQ-B9, OQ-C5 |
| Testing | **Table-driven transition tests** (every allowed and forbidden pair); concurrency tests (two tabs → 412; double start → one application); expiry job; invalidation rules |
| Done when | Applicant can start, consent, choose the program, save and resume business details across sessions and devices |

### Phase 4 — PAN verification and duplicate prevention

| Item | Detail |
|---|---|
| Modules | `verification` (PAN), `integrations` (EPS PAN adapter + resilience + mock adapter) |
| APIs | A12 |
| DB | `verification`; generated column `active_pan_key` + unique index (MySQL's stand-in for a partial unique index) |
| Dependencies | OQ-B5 (holder types), OQ-I2 (PAN endpoint + schema), OQ-C3 (name-match threshold), OQ-B6 |
| Testing | Provider mock scenarios (valid, invalid, timeout, 5xx); idempotent replay without a second provider call; duplicate PAN across applications; name-match flagging |
| Done when | PAN step completes against EPS UAT; duplicates blocked; timeouts reconcile |

### Phase 5 — Email verification, signatory KYC (DigiLocker), live photo

| Item | Detail |
|---|---|
| Modules | `verification` (email, Aadhaar / DigiLocker, liveness), reconciliation jobs |
| APIs | A13–A16, `LIVE_PHOTO` submit |
| DB | `verification` rows of types EMAIL, AADHAAR, LIVENESS |
| Dependencies | OQ-C1 (Aadhaar method + storage), OQ-I4 (DigiLocker contract, callbacks), OQ-I7 (liveness provider), OQ-P4 (live photo mandatory? face match?), OQ-B12 |
| Testing | DigiLocker happy path, number mismatch (`2443`), incomplete (`1709`), abandoned session reconciliation; proof that no Aadhaar number is persisted (DB scan test) |
| Done when | KYC completes in staging with the provider sandbox; browser-close recovery works |

### Phase 6 — Documents and bank verification

| Item | Detail |
|---|---|
| Modules | `documents` (presigned upload, scan pipeline, versions), `verification` (bank) |
| APIs | A17–A25 |
| DB | `document`, `bank_account` |
| Dependencies | OQ-P5 (document list per entity), OQ-P6 (bank mandatory?), ClamAV deployment, OQ-I6 (bank verification contract) |
| Testing | Magic-byte checks, oversize, EICAR test file → quarantine, PDF-with-JS rejection, expired URL, hash mismatch; penny-drop success / failure / name mismatch → manual review; max attempts |
| Done when | Required docs upload and scan cleanly; bank verification works in UAT |

### Phase 7 — White-label setup, agreement + e-sign, submission

| Item | Detail |
|---|---|
| Modules | `white-label` (availability, reservation, branding draft), `agreement` (templates, PDF rendering, e-sign sessions, webhooks, signed archive) |
| APIs | A26–A29, W1–W3, auto-submit T3, A30 timeline |
| DB | `domain_reservation`, `branding_draft`, `agreement`, `esign_request` |
| Dependencies | OQ-B10 (single agreement, template, signer), OQ-I3 (e-sign provider + webhook spec), OQ-T4 (zone), OQ-P8, OQ-P10 |
| Testing | Concurrent sub-domain reservation (one winner); webhook signature / replay / out-of-order; "client says signed but provider says not" stays unsigned; agreement superseded on upstream change |
| Done when | **Vertical slice demo**: start → … → e-sign → `SUBMITTED` in staging |

### Phase 8 — Internal review, corrections, approval (staff console)

| Item | Detail |
|---|---|
| Modules | `review`, staff console (FE-8), notifications for correction / approval / rejection |
| APIs | S1–S13, S21 |
| DB | `review_action`, `correction_request` |
| Dependencies | OQ-O1 (review team / roles), OQ-O3 (maker-checker levels), OQ-O5 (SLA), reason-code catalogue (product) |
| Testing | RBAC matrix tests per endpoint; maker-checker violation; correction loop with re-sign; PII reveal audited; staff cannot edit applicant data |
| Done when | A reviewer can take a submitted case to approved / rejected / corrected in staging |

### Phase 9 — Tenant provisioning (saga) and Local Admin creation

| Item | Detail |
|---|---|
| Modules | `provisioning`, `tenant` (tenant, local admin, domain binding), EPS + org-meta adapters |
| APIs | S14, S15 |
| DB | `provisioning_run`, `provisioning_step`, `tenant`, `tenant_domain`, `local_admin`, `tenant_branding` |
| Dependencies | **OQ-T2** (EPS create org / user / bind domain / activate APIs, idempotency), OQ-B2 (user type), OQ-T3 (wildcard DNS / TLS), OQ-T10 (org cache purge) |
| Testing | Saga step failures at every position + resume; duplicate approval events → one tenant; compensation (deactivate) paths; EPS contract tests (Pact) |
| Done when | Approval in staging creates an inactive org + admin in EPS UAT, resolvable at `{label}.{zone}` but not loggable-into |

### Phase 10 — Go-live checklist, CRM sync, activation

| Item | Detail |
|---|---|
| Modules | `tenant` (go-live tasks, activation gate), CRM adapter, branding publish |
| APIs | S16–S18 |
| DB | `go_live_task` |
| Dependencies | OQ-O2 (blocking tasks), OQ-I5 (CRM API), OQ-P7 (Play Store scope) |
| Testing | Activation gate negative cases; partial activation failure (EPS call fails after the local commit) retries; welcome notification sent once |
| Done when | End-to-end: Lead → … → Local Admin logs into the new portal on the existing `wlc-webapp` |

### Phase 11 — Custom domains

| Item | Detail |
|---|---|
| Modules | `tenant` domains, DNS verifier, certificate issuer |
| APIs | T1–T3 |
| Dependencies | OQ-T3 / T4, edge / LB capability, OQ-T9 (tenant auth into this service) |
| Testing | TXT present / absent / wrong; domain claimed by another tenant; TLS failure surfaces; host-header spoofing does not authorize |
| Done when | A tenant can attach and serve a custom domain in staging |

### Phase 12 — Hardening and production readiness

| Item | Detail |
|---|---|
| Objective | Bank-grade launch |
| Work | External penetration test + fixes; threat-model review; load test (OTP and status endpoints); DR drill (PITR restore, bucket restore); key rotation drill; runbooks (provider outage, provisioning failure, stuck webhooks); dashboards + alerts; data-retention jobs; DPIA / compliance sign-off; staff training; migration plan for in-flight applications (OQ-O4); retire the Go-Live tool |
| Done when | Go-live checklist signed by engineering, security, compliance, ops |

### Frontend parallel track (not part of this backend plan's code, listed for coordination)

| FE phase | Work |
|---|---|
| FE-2 | Onboarding host / app decision (OQ-P2); BFF session route; OTP screens (reuse `LoginPanel` patterns) |
| FE-3 | New `OnboardingServices` implementation / REST adapter; init steps from the snapshot; persist skips; consent + program screens (reuse `RoleSelection`) |
| FE-4–7 | Reuse `BusinessDetailsStep`, `DigilockerRedirectionStep`, `VideoKycStep`, `AddBankAccountStep`, `SignAgreementStep`; remove client-side success shortcuts; documents and white-label screens; summary / declaration; status page |
| FE-8 | Staff console |
| FE-10–11 | Tenant-side domain settings |

---

## 3. Testing strategy (designed, not implemented)

### 3.1 Unit tests

| Area | What |
|---|---|
| Validation | Every Bean Validation rule, positive + negative, with fixtures shared with the frontend's generated Zod schemas (PAN, IFSC, mobile, pincode, sub-domain, names, addresses) |
| Business rules | Step policy per program / entity; dependency invalidation; activation gate; maker-checker; holder-type rules |
| State transitions | Table-driven: all `(state, event)` pairs → expected result or `INVALID_STATE_TRANSITION` |
| Authorization | Spring Security method rules and policy functions per permission |
| Tenant isolation | Repository scope enforcement (ArchUnit build rule + runtime default-deny resolver) |
| Duplicate prevention | Idempotency store; dedup keys; notification dedup |
| Crypto | Encrypt / decrypt round-trip, blind-index stability, masking, redaction allow-list |

### 3.2 API tests (Spring Boot Test + REST Assured against the app + a real MySQL 8.4 via Testcontainers)

| Category | Examples |
|---|---|
| Valid requests | Each endpoint's happy path, snapshot shape, ETag increments |
| Invalid requests | Schema violations → `422` with `invalid_params` |
| Missing fields | Required fields per step |
| Unauthorized | No session → `401`; wrong application → `404`; staff without permission → `403`; tenant A token on tenant B → `404` |
| Duplicate requests | Same `Idempotency-Key` → identical replay; reused key with a different body → `422` |
| Invalid transitions | Submit before steps are complete; approve from `SUBMITTED`; activate before the gate |
| Concurrency | Parallel writes with the same `If-Match` → one `200`, one `412` |
| Scope safety net (replaces the RLS test) | Every repository call with no principal in context → exception, zero rows; every endpoint called with tenant A's principal on tenant B's ids → `404` |
| Constraints | Generated-column unique keys: two active applications for one PAN → `409 PAN_ALREADY_IN_USE`; the same PAN after rejection → allowed |

### 3.3 Integration tests (provider adapters against mocks; staging against sandboxes)

| Integration | Scenarios |
|---|---|
| PAN | valid, invalid, holder-type mismatch, timeout, 5xx, malformed response |
| KYC / DigiLocker | success, mobile mismatch, incomplete, abandoned, duplicate callback |
| Bank | success, invalid account, name mismatch, async result, timeout |
| Documents | clean, infected (EICAR), wrong type, oversize, expired URL |
| e-Sign | signed via webhook, signed via poll only, declined, expired, forged webhook, replayed webhook, out-of-order |
| Domain | TXT ok / missing / wrong, CNAME wrong, cert failure |
| CRM | success, 4xx (data issue → dead-letter), 5xx (retry) |
| EPS provisioning | Pact JVM consumer contracts for each interaction used; lookup-before-create behaviour |

### 3.4 End-to-end tests (Playwright on the frontends, staging with sandboxes)

Full lifecycle:

```text
Lead → invite → start onboarding (OTP) → consent → program → PAN → business details
→ email → signatory KYC → live photo → documents → bank → white-label → declaration
→ agreement e-sign → SUBMITTED → review → approval (maker-checker) → provisioning
→ go-live tasks → activation → Local Admin logs into {label}.{zone}
```

Plus these journeys:

| Journey | Assertion |
|---|---|
| Failure | PAN invalid → retry → success; attempts exhausted → blocked |
| Retry | Provider timeout → `PENDING_EXTERNAL` → reconciliation completes |
| Resume | Close the browser mid-DigiLocker; return next day on another device → state intact |
| Rejection | Reviewer rejects → applicant sees the reason; sub-domain released |
| Correction | Reviewer reopens DOCUMENTS + BUSINESS_DETAILS → applicant fixes → re-sign required → resubmit |
| Duplicate requests | Double-click submit / approve → single effect; single notification |
| External failures | CRM down during approval → activation still possible; CRM catches up |
| Isolation | Tenant A admin cannot see tenant B's domains / branding via the API, even from B's host |

### 3.5 Non-functional

- Load: OTP send/verify and the snapshot endpoint at 10× expected peak.
- Security: OWASP ASVS L2 checklist; dependency and container scans in CI; pen test before launch.
- Chaos (staging): kill the worker mid-saga; drop the DB connection mid-transition; provider latency injection.
- DR: restore drill meeting RPO ≤ 5 min / RTO ≤ 1 h (ASSUMPTION, confirm with ops).

---

## 4. Definition of done (every phase)

1. Code reviewed by two engineers (one from the owning team).
2. Unit + API + integration tests for the phase are green in CI. Coverage ≥ 80 % on domain modules.
3. OpenAPI updated; breaking-change check passes.
4. Audit events and metrics for every new state transition.
5. No PII in logs (automated scrubbing test passes).
6. Runbook entry for every new failure mode.
7. Security review for any new external integration or data class.
8. Docs in `docs/` updated where behaviour changed from this blueprint.

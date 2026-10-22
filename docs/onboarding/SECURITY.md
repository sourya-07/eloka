# ELOKA Local Admin Onboarding — Security Architecture

> Status: **DRAFT for architecture review** · **Revised 14 October 2026:** Java 25 + Spring Boot 4, MySQL 8.4 · Phase 0
> Bar set by the business context (whiteboard / notes, **CONFIRMED**): "RBI Guidelines follow", "follow all test cases of RBI", "DP Act (read)", "Build Bank Grade", "User's onboarding journey completely, security purpose".
> Specific regulatory obligations below are flagged for **compliance confirmation**. This document is an engineering design, not legal advice.

Labels: **CONFIRMED** · **EXISTING FRONTEND BEHAVIOUR** · **PROPOSED / RECOMMENDED** · **ASSUMPTION** · **OPEN QUESTION**

---

## 1. Threat model (summary)

| Asset | Threats | Primary controls |
|---|---|---|
| Applicant identity (mobile) | OTP brute force, SIM-swap, enumeration | Hashed OTP, attempt limits, lockouts, uniform responses, OTP re-auth for sensitive actions |
| PAN, bank account, Aadhaar-derived data | Leakage via DB dump, logs, staff over-access, provider logs | Field encryption (KMS envelope), HMAC blind indexes, masking, redacting logger, reveal-with-audit |
| Documents (KYC, agreements, photos) | Public URL leakage, malware upload, tampering | Private bucket, short presigned URLs, scanning, hashing, Object Lock |
| Agreement validity | Forged "signed" status from the client | Server-side provider confirmation; webhook signature + status API |
| Tenant isolation | Cross-tenant reads (IDOR), host-header spoofing | Principal-scoped authorization, repository scoping (Hibernate tenant filter), cross-tenant tests, per-role DB users, host never authorizes |
| Staff console | Account takeover, insider misuse | SSO + MFA, RBAC, maker-checker, PII reveal audit, IP allow-list / VPN |
| Webhooks | Forgery, replay | HMAC signature, timestamp window, dedup, status re-confirmation |
| Provisioning | Duplicate tenants, orphan active admins | Idempotent saga, invariants, activation gate |
| Availability | OTP-pumping (SMS cost fraud), DoS | Edge rate limits, per-mobile / per-IP quotas, CAPTCHA after thresholds (PROPOSED) |

---

## 2. Sensitive data classification and handling

| Data | Class | Collected at | Stored as | Who can see | Retention (ASSUMPTION, OQ-C2) |
|---|---|---|---|---|---|
| Mobile | PII | Step 1 | enc + hash + masked | Applicant; staff masked; reveal audited | Life of relationship + statutory period |
| Email | PII | Step 6 | enc + hash | Same | Same |
| Business PAN | Sensitive PII / business identifier | Step 4 | enc + hash + masked + holder type | Same | Same |
| Aadhaar number | **Restricted** | Never stored | — | — | — |
| Aadhaar-derived (name, YoB, masked last 4, provider ref) | Sensitive PII | Step 7 | Plain name; masked number; ref | Staff | Same |
| Aadhaar photo / XML | **Restricted** | Only if face match is required | Object storage, short TTL | System only | Delete after the face match (PROPOSED) |
| Live photo | Biometric-adjacent PII | Step 8 | Object storage (SSE-KMS) | Staff with `pii.reveal` | Same as KYC records |
| Bank account | Financial PII | Step 10 | enc + hash + masked | Staff masked | Same |
| Business address, signatory name | PII | Step 5 | Plain (needed for agreement / CRM); masked in list views | Staff | Same |
| Signature image | PII | Step 5 | Object storage | Staff, agreement renderer | Same |
| Signed agreement | Legal record | Step 13 | Object storage + Object Lock | Staff, applicant | Contract term + legal limitation period |
| OTP | Secret | Steps 1 / 6 | HMAC only, 5 min | Nobody | Purged after 24 h |
| Session tokens | Secret | — | Hash only | Nobody | Expiry + 30 days |
| Provider credentials | Secret | — | Secrets manager | Service identity | Rotated |

**Data minimisation (PROPOSED, DPDP Act 2023 principle):**
- collect only what the step policy requires for the program and entity type,
- never store full Aadhaar,
- never persist raw provider responses,
- purge drafts of withdrawn or expired applications per the retention schedule.

---

## 3. Authentication

| Population | Mechanism | Session | Notes |
|---|---|---|---|
| Applicant | Mobile OTP (6 digits ASSUMPTION; EPS today uses 4, with comments saying "3 for new users, 4 for existing", `VerifyOtp.jsx:262`) | Opaque token (≥256-bit random) stored hashed; cookie `__Host-onb_session` httpOnly, Secure, SameSite=Strict, Path=/; idle 30 min, absolute 12 h; rotated on privilege change | Today's frontend keeps tokens in `sessionStorage` (readable by any XSS). The onboarding flow keeps the token out of JS via the BFF. |
| Applicant step-up | OTP re-auth (`X-Reauth-Token`, valid 5 min) | — | Required for: bank account submit/change, e-sign session start, withdraw, email change |
| Staff | OIDC SSO, IdP-enforced MFA (`amr` must include `mfa`/`otp`/`hwk`) | Short-lived access token (≤15 min) + IdP refresh; console session 8 h | Replaces the Go-Live tool's email + password and its "Request New ID" self-service |
| Tenant users (post-activation) | Existing EPS login (OTP / Google) | Existing | Unchanged (OQ-T9 for calls into this service) |
| Services | mTLS or OAuth2 client credentials | — | EPS, org-meta |
| Webhooks | Provider HMAC signatures | — | §10 |

### OTP security (PROPOSED)

| Control | Value |
|---|---|
| Generation | CSPRNG; numeric; length per provider |
| Storage | `HMAC-SHA256(server_pepper, otp_ref ‖ otp)` |
| Expiry | 5 min |
| Verify attempts | 5 per OTP, then the OTP is invalidated |
| Send limits | 30 s gap; 5 / mobile / hour; 10 / mobile / day; 10 / IP / hour; global anomaly alert |
| Lockout | 15 min after 3 invalidated OTPs in an hour |
| Responses | Identical for known and unknown numbers (no enumeration) |
| Logging | Never log the OTP; log the masked mobile + outcome |
| Delivery | DLT-approved template; no OTP in URLs |

---

## 4. Authorization and tenant isolation

### 4.1 RBAC (staff)

| Role | Key permissions |
|---|---|
| `CS_REVIEWER` | `onboarding.read`, `onboarding.review`, document verify / reject, corrections, recommend |
| `APPROVER` | `onboarding.read`, `onboarding.approve`, `onboarding.reject` |
| `COMPLIANCE` | `onboarding.read`, `onboarding.hold`, `pii.reveal`, `audit.read` |
| `OPS` | `onboarding.provision`, `tenant.golive`, `onboarding.ops` (extend / expire), `lead.*` |
| `TENANT_ACTIVATOR` | `tenant.activate` |
| `SALES` | `lead.read`, `lead.write` |
| `AUDITOR` | read-only + `audit.read` (no reveal) |
| `SUPER_ADMIN` | role management only. Does **not** include approve / activate by default (separation of duties). |

Rules:
- maker ≠ checker (recommender ≠ approver; approver ≠ activator, OQ-O3),
- the permission check happens in a guard per route **and** again in the domain service for state transitions,
- reviewers cannot edit applicant data. They can only request corrections.

### 4.2 Applicant authorization

Every applicant route resolves `application_id` from the **session** (not from the path alone). A path id that differs from the session's application → `404`.

### 4.3 Tenant isolation (two walls + safety nets)

1. **Policy:** principal → scope (`application_id` for applicants; `tenant_id` from the EPS token for tenant users; RBAC for staff).
2. **Repository:** every repository method requires a `Scope` argument and adds the scope predicate. Tenant-scoped entities use Hibernate's `@TenantId`, resolved from the authenticated principal by a `CurrentTenantIdentifierResolver`; pre-tenant entities are always loaded by `(application_id from the session, id)`. With no principal in context the resolver throws, so the default is **deny**. An **ArchUnit** rule fails the build if code outside repositories uses `JdbcClient` / native queries, or if a native query lacks a scope parameter.

**Why only two walls:** the first draft had a third wall, PostgreSQL Row-Level Security. The team chose **MySQL**, which has no RLS. These safety nets take its place:

- **Cross-tenant tests in CI** for every endpoint (below). A failing test blocks the merge.
- **Separate MySQL users with minimal grants:** `onb_api` (DML on its tables, `INSERT`/`SELECT` only on `audit_event`), `onb_worker`, `onb_migrate` (DDL, used only by Flyway in the deploy job), `onb_readonly` (reporting, masked views only). None of them can read `ekodb`.
- **Code review rule:** any new native SQL needs a second reviewer from the security checklist.
- **Staff access** runs through separate, permission-checked code paths and is always audited (staff id + reason).

Residual risk (accepted by choosing MySQL): a bug in the scoping code has no database-level backstop.

**The domain is never an authorization mechanism.** The host / sub-domain only picks the branding (`org_detail`). A Local Admin of tenant A calling from `tenantB.zone` with A's token sees only A. Requests with B's ids return `404`.

Automated tests (IMPLEMENTATION_PLAN.md): for every endpoint, a cross-scope test (applicant X → application Y; tenant A token → tenant B ids; host spoofing) must return `404` / `403`.

---

## 5. Input validation

- **Jakarta Bean Validation** on every request DTO at the API edge. Unknown JSON fields are rejected (Jackson `FAIL_ON_UNKNOWN_PROPERTIES`). The frontend gets matching **Zod** schemas generated from the OpenAPI spec (Orval), so both sides apply the same rules.
- Canonicalisation before validation: trim, upper-case PAN / IFSC, NFC-normalise names, strip zero-width characters.
- Regexes aligned with the frontend:
  - PAN `^[A-Z]{5}[0-9]{4}[A-Z]$`
  - IFSC `^[A-Z]{4}0[A-Z0-9]{6}$`
  - mobile `^[6-9]\d{9}$`
  - pincode 6 digits
  - names / addresses per `src/constants/validation.ts`
  - sub-domain label `^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$`
- Length caps on every string; JSON body limit 64 KB (files never go through the API).
- Output encoding: the API returns JSON only; the staff console escapes everything (no `dangerouslySetInnerHTML` for applicant data).
- Free-text fields (reviewer messages) are stored as plain text and rendered as text.

---

## 6. API security

| Control | Design |
|---|---|
| Transport | TLS 1.2+ (1.3 preferred), HSTS (the frontend already sets HSTS with `includeSubDomains`, `next.config.js:162`) |
| CORS | API not exposed cross-origin. The browser only talks to the BFF on the same origin. |
| CSRF | SameSite=Strict cookie + double-submit token |
| Rate limiting | Edge (per IP, per route class) + app (per mobile, per application, per staff) |
| Idempotency | Required on side-effect POSTs |
| Error handling | Problem+json with stable codes. No stack traces, SQL or provider messages to clients. Provider details go to logs only, redacted. |
| Enumeration | `404` for out-of-scope ids; uniform OTP responses; generic duplicate messages to applicants |
| Headers | `Cache-Control: no-store` on all applicant / staff responses with PII; `X-Content-Type-Options: nosniff`; strict CSP on the UIs |
| Admin surface | `/v1/admin/*` restricted to the corporate network / VPN or an IP allow-list at the edge, in addition to SSO (PROPOSED) |
| Dependency hygiene | Lockfile, `npm audit` / Dependabot, CodeQL, Sonar, Trivy image scans, gitleaks (already used in the repo) |

---

## 7. File and document security

| Control | Design |
|---|---|
| Upload path | Presigned PUT only. The key is generated by the server (`{env}/quarantine/{application_id}/{document_id}/{version}`). Content-Type, Content-Length and `x-amz-checksum-sha256` are bound in the signature. 5 min expiry. |
| Allowed types | PDF, JPEG, PNG (logo: PNG, or SVG sanitised / rasterised). Checked by **magic bytes** after upload, not by extension. |
| Size limits | Docs ≤ 10 MB, photos ≤ 5 MB, logo ≤ 1 MB (ASSUMPTION) |
| Malware scanning | ClamAV on every object. PDFs also checked for JavaScript / launch actions / embedded files (reject). Images re-encoded to strip EXIF (except where location evidence is needed, and then kept as metadata, not in the image). |
| Quarantine | Unscanned objects are unreadable by the app role (bucket policy on prefix). Clean objects are copied to `clean/`. |
| Access | Presigned GET (60 s) issued only after an authorization check; downloads audited (`DOCUMENT_VIEWED`) for staff |
| Encryption | SSE-KMS with a per-environment CMK. Optional client-side encryption with the per-application DEK for crypto-shredding (OQ-C2). |
| Integrity | SHA-256 stored at upload and re-verified on retrieval for agreements |
| Versioning | Bucket versioning on; DB `document.version` + `supersedes_id` |
| Retention | Lifecycle rules per document class; Object Lock (compliance mode) for signed agreements |
| Verification / rejection | Reviewer actions recorded with reason codes; rejected docs remain for audit until retention ends |

---

## 8. Encryption and secret management

- **At rest:** managed MySQL storage encryption (InnoDB tablespace / RDS encryption at rest) plus **application-level field encryption** (AES-256-GCM via **Google Tink**) for PAN, account number, mobile and email. The DEK is per application, wrapped by a KMS CMK, and cached in memory for at most 5 min.
- **Blind indexes:** `HMAC-SHA256(index_key, normalised_value)` with a key separate from the encryption key. Enables dedup and exact search without decryption.
- **Key rotation:**
  - CMK rotated yearly (KMS),
  - DEKs re-wrapped on rotation,
  - index-key rotation needs re-hashing (planned job).
- **Secrets:** secrets manager only (provider keys, EPS client credentials, OTP pepper, cookie signing keys). Nothing in env files committed to git. The existing repo practice of `.env.*` files must not extend to backend secrets.
- **Crypto-shredding:** destroying an application's DEK makes its encrypted fields and client-side-encrypted documents unreadable. Used at retention expiry.

---

## 9. Audit logging

### 9.1 What every audit event captures

`actor_type`, `actor_id`, `action`, `entity_type`, `entity_id`, `application_id`, `tenant_id`, `from_state`, `to_state`, `reference_id`, `correlation_id`, `source`, `occurred_at`, hashed IP / UA, allow-listed `metadata`, `prev_hash` / `hash`. **No PAN, Aadhaar, account numbers, OTPs or document contents.**

### 9.2 Event catalogue (final)

| Group | Events |
|---|---|
| Lead | `LEAD_CREATED`, `LEAD_QUALIFIED`, `LEAD_INVITED`, `LEAD_DISQUALIFIED`, `LEAD_CONVERTED` |
| Identity | `OTP_SENT`, `OTP_VERIFY_FAILED`, `OTP_LOCKED`, `MOBILE_VERIFIED`, `SESSION_CREATED`, `SESSION_REVOKED`, `REAUTH_SUCCEEDED`, `STAFF_LOGIN`, `STAFF_ROLE_CHANGED` |
| Application | `ONBOARDING_STARTED`, `ONBOARDING_RESUMED`, `CONSENT_GIVEN`, `CONSENT_WITHDRAWN`, `PROGRAM_SELECTED`, `STEP_SAVED`, `STEP_SUBMITTED`, `STEP_SKIPPED`, `STEP_INVALIDATED`, `STEP_BLOCKED`, `DECLARATION_ACCEPTED`, `APPLICATION_SUBMITTED`, `APPLICATION_WITHDRAWN`, `APPLICATION_EXPIRED`, `APPLICATION_EXTENDED` |
| Verification | `PAN_SUBMITTED`, `PAN_VERIFIED`, `PAN_VERIFICATION_FAILED`, `DUPLICATE_PAN_DETECTED`, `EMAIL_VERIFIED`, `KYC_STARTED`, `KYC_COMPLETED`, `KYC_FAILED`, `LIVE_PHOTO_UPLOADED`, `FACE_MATCH_PASSED`, `FACE_MATCH_FAILED`, `BANK_SUBMITTED`, `BANK_VERIFIED`, `BANK_VERIFICATION_FAILED`, `VERIFICATION_MANUAL_DECISION` |
| Documents | `DOCUMENT_UPLOAD_REQUESTED`, `DOCUMENT_UPLOADED`, `DOCUMENT_QUARANTINED`, `DOCUMENT_VERIFIED`, `DOCUMENT_REJECTED`, `DOCUMENT_VIEWED`, `DOCUMENT_SUPERSEDED` |
| Agreement | `AGREEMENT_GENERATED`, `ESIGN_STARTED`, `AGREEMENT_SIGNED`, `ESIGN_FAILED`, `AGREEMENT_SUPERSEDED` |
| White label | `SUBDOMAIN_RESERVED`, `SUBDOMAIN_RELEASED`, `BRANDING_DRAFT_SAVED`, `BRANDING_APPROVED`, `BRANDING_PUBLISHED`, `BRANDING_UPDATED` |
| Review | `APPLICATION_CLAIMED`, `APPLICATION_RELEASED`, `CORRECTION_REQUESTED`, `APPROVAL_RECOMMENDED`, `APPLICATION_APPROVED`, `APPLICATION_REJECTED`, `APPLICATION_HELD`, `HOLD_RELEASED`, `PII_REVEALED`, `MAKER_CHECKER_VIOLATION_BLOCKED` |
| Provisioning / tenant | `PROVISIONING_STARTED`, `PROVISIONING_STEP_SUCCEEDED`, `PROVISIONING_STEP_FAILED`, `PROVISIONING_FAILED`, `PROVISIONING_SUCCEEDED`, `TENANT_CREATED`, `LOCAL_ADMIN_CREATED`, `GO_LIVE_TASK_UPDATED`, `DOMAIN_SUBMITTED`, `DOMAIN_VERIFIED`, `DOMAIN_FAILED`, `TENANT_ACTIVATED`, `LOCAL_ADMIN_ACTIVATED`, `TENANT_SUSPENDED` |
| Integrations | `WEBHOOK_RECEIVED`, `WEBHOOK_REJECTED` (bad signature), `PROVIDER_CIRCUIT_OPENED` |
| Security | `DUPLICATE_LOCAL_ADMIN_ATTEMPT`, `CROSS_SCOPE_ACCESS_DENIED`, `RATE_LIMIT_TRIGGERED` |

Tamper evidence: per-application hash chain, plus a nightly export to WORM storage. Audit reads are themselves audited (`audit.read`).

---

## 10. Webhook verification and replay protection

1. Verify the provider signature (HMAC-SHA256 or the provider's scheme, **EXTERNAL API DETAILS REQUIRED**) over the raw body. Constant-time compare.
2. Reject if the timestamp is outside ±5 min.
3. Dedup on `(provider, provider_event_id)` (or a payload hash if no id).
4. Optional IP allow-list for provider ranges.
5. Never trust the payload for state: confirm with the provider status API before applying `SIGNED` / `VERIFIED`.
6. Return `200` quickly; process async.

---

## 11. Logging, monitoring and privacy

- **Structured logs** with `correlation_id`. A redaction allow-list (only listed fields are logged) instead of a deny-list. PII patterns scrubbed as a second net. Test fixtures assert that log lines contain no PAN-like or account-like patterns. Do **not** anchor secret-detection regexes with `\b`: an identifier next to `_` is a word character and escapes detection.
- **No PII in URLs** (query strings land in edge logs).
- **Error tracking** (Sentry) with PII scrubbing, no request bodies.
- **Security monitoring alerts:** OTP spikes per prefix / IP, repeated cross-scope denials, webhook signature failures, PII reveal volume per staff, after-hours approvals.
- **Retention:** application logs 30–90 days; audit per the retention schedule (OQ-C2).

---

## 12. Data retention and deletion (OQ-C2)

| Situation | Proposed handling (to be confirmed by compliance) |
|---|---|
| Expired / withdrawn before submit | Delete drafts and documents after N days (ASSUMPTION 90); keep a minimal audit stub |
| Rejected | Keep for the statutory / defence period (unknown), then crypto-shred |
| Active Local Admin | Keep KYC records for the relationship + the period mandated by applicable RBI KYC directions (commonly cited as 5 years after the relationship ends; **verify applicability**) |
| Consent withdrawn | Stop processing; retain only what law requires |
| Data principal requests (DPDP) | Access / correction via support workflow; erasure where not overridden by legal retention |

---

## 13. Findings from the existing frontend that matter for security

| # | Finding | Recommendation |
|---|---|---|
| F1 | DigiLocker link fetched from the browser via the tf tunnel with a hard-coded UAT path `/ekoicici/v1/marketuat` (HEAD) | Server-side session creation, environment config |
| F2 | e-sign success accepted from any `postMessage` `STATUS_UPDATE` with no origin check (HEAD) | Check origin, and treat it only as a hint to re-fetch server status |
| F3 | Client sends `esign_completed: true` (HEAD) | Ignore; server truth only |
| F5 | Geolocation fallback coordinates sent as real (HEAD) | `location_source` flag |
| F8 | Hard-coded identities (`user_code 99029899`, `initiator_id 1234567891`, fixed `realsourceip`, `org_id 26`) in WIP request bodies | Never ship. Derive identity from the session on the server. |
| F9 | `client_ref_id = Date.now()` used as the only request reference | Real idempotency keys |
| — | Tokens in `sessionStorage` (XSS-readable) | BFF cookie for the onboarding flow |
| — | `ORG_META_SERVICE_SECRET` sent in the request body | Header or mTLS for new service calls |
| — | Inventory GraphQL `Authorization: "11205980"` hard-coded (`src/lib/inventory/gql.ts:17`) | Out of scope; report to the platform team |
| — | Go-Live tool shows raw API responses and uses local passwords (doc screenshots) | Retire it in favour of the SSO staff console |
| — | Agreements and KYC documents emailed to a named CS person (doc step 12) | Queue + authenticated document access |

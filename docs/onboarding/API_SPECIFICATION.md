# ELOKA Local Admin Onboarding — API Specification

> Status: **DRAFT for architecture review** · **Revised 14 October 2026:** Java 25 + Spring Boot 4, MySQL 8.4 · Phase 0 (contract design only. **No APIs are implemented.**)
> All endpoints are **PROPOSED / RECOMMENDED** unless marked otherwise. Existing EPS interactions they wrap are listed in [INTEGRATIONS.md](./INTEGRATIONS.md).

---

## 1. Conventions

| Topic | Convention |
|---|---|
| Base path | `/v1`. Applicant: `/v1/onboarding/…`. Staff: `/v1/admin/…`. Tenant (post-activation): `/v1/tenants/{tenantId}/…`. Providers: `/v1/webhooks/{provider}` |
| Format | JSON, UTF-8, `camelCase` is **not** used. Field names stay `snake_case` to match the existing frontend and EPS payloads (e.g. `authorized_signatory_name`, `current_address_pincode`). |
| IDs | Public, prefixed: `onb_01J…`, `doc_…`, `ver_…`, `agr_…`, `ten_…`, `lead_…` |
| Auth | Applicant: `__Host-onb_session` cookie (httpOnly, Secure, SameSite=Strict) set by the BFF. The BFF forwards it as `Authorization: Session <opaque>` to the API. Staff: `Authorization: Bearer <OIDC access token>`. Tenant: EPS-issued token (OQ-T9). Webhooks: provider signature header. |
| CSRF | Same-site cookie + double-submit `X-CSRF-Token` on every non-GET applicant call |
| Concurrency | Application-scoped responses carry `ETag: "v<version>"`. Writes to the application or its steps **require** `If-Match`. Mismatch → `412 PRECONDITION_FAILED` + current snapshot. |
| Idempotency | `Idempotency-Key: <uuid>` **required** on every POST that has side effects (marked 🔁). Replays within 24 h return the original status and body. A different body with the same key → `422 IDEMPOTENCY_KEY_REUSED`. |
| Correlation | `X-Request-Id` accepted or generated, echoed, logged and stored in audit |
| Errors | RFC 9457 `application/problem+json`: `{ type, title, status, code, detail, invalid_params?: { field: message }, retry_after?, current?: snapshot }`. `invalid_params` keeps the shape the frontend already parses. |
| Pagination | Cursor: `?limit=50&cursor=…` → `{ items, next_cursor }` |
| Time | RFC 3339 UTC |
| Rate limits | Headers `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`; `429` with `Retry-After` |
| Masking | PAN `ABCDE****F`, mobile `******1234`, account `XXXXXX6789`, Aadhaar `XXXX-XXXX-1234` (never more) |
| Versioning | Breaking changes → `/v2`. Additive fields are allowed in `/v1`. |

### 1.1 Standard error codes

| HTTP | `code` | Meaning |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | Not JSON / wrong types |
| 401 | `UNAUTHENTICATED`, `OTP_INVALID`, `SESSION_EXPIRED` | — |
| 403 | `FORBIDDEN`, `STEP_NOT_EDITABLE` | Permission or scope |
| 404 | `NOT_FOUND` | Also returned for out-of-scope ids (no existence oracle) |
| 409 | `INVALID_STATE_TRANSITION`, `PAN_ALREADY_REGISTERED`, `SUBDOMAIN_TAKEN`, `DUPLICATE_APPLICATION`, `PREREQUISITE_INCOMPLETE` | — |
| 410 | `OTP_EXPIRED`, `UPLOAD_URL_EXPIRED`, `ESIGN_SESSION_EXPIRED` | — |
| 412 | `PRECONDITION_FAILED` | ETag mismatch |
| 413 | `FILE_TOO_LARGE` | — |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | — |
| 422 | `VALIDATION_FAILED` (+ `invalid_params`), `VERIFICATION_FAILED`, `NAME_MISMATCH`, `CONSENT_VERSION_STALE`, `IDEMPOTENCY_KEY_REUSED` | — |
| 423 | `STEP_BLOCKED` | Attempts exhausted |
| 429 | `RATE_LIMITED`, `OTP_LOCKED` | — |
| 502 / 504 | `PROVIDER_ERROR`, `PROVIDER_TIMEOUT` | Step goes to `PENDING_EXTERNAL`; retry later |
| 503 | `SERVICE_UNAVAILABLE` | Circuit open |

---

## 2. Application snapshot (returned by most applicant calls)

```json
{
  "onboarding_id": "onb_01JABC...",
  "status": "IN_PROGRESS",
  "version": 14,
  "program": { "applicant_category": "ENTERPRISE", "product_type": "WHITE_LABEL", "entity_type": 1 },
  "next_recommended_step": "BANK_ACCOUNT",
  "expires_at": "2026-11-04T10:00:00Z",
  "steps": [
    { "step_key": "MOBILE_VERIFICATION", "status": "COMPLETED", "required": true },
    { "step_key": "BUSINESS_PAN", "status": "COMPLETED", "required": true,
      "summary": { "pan_masked": "ABCDE****F", "name_as_per_pan": "SHLOK ENTERPRISE" } },
    { "step_key": "SIGNATORY_KYC", "status": "PENDING_EXTERNAL", "required": true },
    { "step_key": "BANK_ACCOUNT", "status": "FAILED", "required": true,
      "attempt_count": 1, "max_attempts": 3, "last_error_code": "BANK_ACCOUNT_INVALID" },
    { "step_key": "DOCUMENTS", "status": "REOPENED", "required": true,
      "reopen_reason_code": "DOC_ILLEGIBLE", "reviewer_message": "GST certificate is blurred" }
  ],
  "available_actions": ["SAVE_STEP", "SUBMIT_STEP", "WITHDRAW"],
  "correction": null,
  "go_live": null
}
```

---

## 3. Applicant APIs

### 3.1 Identity and session

| # | Method + path | Auth | Request | Response | Errors | State transition | Idempotency | Rate limit |
|---|---|---|---|---|---|---|---|---|
| A1 | `POST /v1/onboarding/otp` 🔁 | none | `{ mobile, invite_token? }` | `202 { otp_ref, expires_in: 300, resend_after: 30, otp_length }` | 422, 429 | — | key + (mobile, 30 s window) | 5 / mobile / h; 10 / IP / h; 30 s resend gap |
| A2 | `POST /v1/onboarding/otp/verify` | none | `{ otp_ref, otp }` | `200 snapshot` + session cookie (`resumed: true/false`) | 401 `OTP_INVALID` (+ `attempts_left`), 410, 429 `OTP_LOCKED` | → `IN_PROGRESS` (T1) or resume | single-use OTP | 5 attempts / OTP |
| A3 | `POST /v1/onboarding/session/refresh` | session | — | `204` (rotated cookie) | 401 | — | — | 60 / h |
| A4 | `DELETE /v1/onboarding/session` | session | — | `204` | — | — | — | — |

Responses are uniform whether or not the mobile is already known (no account-existence oracle). An active Local Admin's mobile gets a generic `409 DUPLICATE_APPLICATION` **only after** OTP verification.

### 3.2 Application and steps

| # | Method + path | Auth | Request | Response | Errors | State transition | Idempotency | Rate limit |
|---|---|---|---|---|---|---|---|---|
| A5 | `GET /v1/onboarding/applications/current` | session | — | `200 snapshot` + `ETag` | 404 (none) | — | safe | 120 / min |
| A6 | `GET /v1/onboarding/applications/{onboarding_id}` | session (owner) | — | `200 snapshot` | 404 | — | safe | 120 / min |
| A7 | `PUT /v1/onboarding/applications/{id}/steps/{step_key}` | session + `If-Match` | `{ draft: {...} }` (step-specific, non-sensitive fields only) | `200 snapshot` | 403 `STEP_NOT_EDITABLE`, 409, 412, 422 | step → `IN_PROGRESS` | natural (PUT) | 60 / min |
| A8 | `POST /v1/onboarding/applications/{id}/steps/{step_key}/submit` 🔁 | session + `If-Match` | `{ data: {...} }` | `200 snapshot` (step `COMPLETED` / `FAILED` / `PENDING_EXTERNAL`) | 409 `PREREQUISITE_INCOMPLETE`, 412, 422, 423 | step transitions; may invalidate dependents | key | 30 / min |
| A9 | `POST /v1/onboarding/applications/{id}/steps/{step_key}/skip` 🔁 | session + `If-Match` | `{ reason? }` | `200 snapshot` | 409 (`required` step) | step → `SKIPPED` | key | — |
| A10 | `POST /v1/onboarding/applications/{id}/submit` 🔁 | session + `If-Match` | `{}` | `200 snapshot` (`SUBMITTED`) | 409 `PREREQUISITE_INCOMPLETE` (+ list), 422 `VERIFICATION_EXPIRED` | T3 / T6 | key | — |
| A11 | `POST /v1/onboarding/applications/{id}/withdraw` 🔁 | session + OTP re-auth (`X-Reauth-Token`) | `{ reason_code }` | `200 snapshot` | 409 | T16 | key | — |

Normally the **agreement signing completion** auto-submits (T3). A10 exists for resubmission after corrections when no re-sign is needed.

Step payloads for A8 (`data`):

| `step_key` | `data` fields | Server checks |
|---|---|---|
| `CONSENT` | `consent_version`, `terms_version`, `purposes[]`, `accepted` | Current versions |
| `PROGRAM_SELECTION` | `applicant_category` (`ENTERPRISE`), `product_type` (`WHITE_LABEL`), `entity_type` (1 Pvt Ltd, 2 LLP, 3 Partnership, 4 Sole Prop) | Allowed by `step_policy`; applies N/A steps |
| `BUSINESS_DETAILS` | `name`, `company_type`, `authorized_signatory_name`, `contact_person_cell`, `alternate_mobile?`, `current_address_line1`, `current_address_line2?`, `current_address_district`, `current_address_state`, `current_address_pincode`, `signature_document_id`, `gstin?` / `cin?` (OQ-P5) | Same regexes as `BusinessDetailsStep`; name match vs PAN |
| `LIVE_PHOTO` | `document_id`, `captured_latlong?`, `location_source` | Document clean; face detect / match |
| `DOCUMENTS` | `{}` (completion check) | All required doc types `PENDING_REVIEW` or better |
| `WHITE_LABEL_SETUP` | `app_name`, `subdomain_label`, `logo_document_id?`, `theme { primary, primary_light, primary_dark, accent, accent_light, accent_dark, navstyle }` | Reservation held by this application |
| `DECLARATION` | `declaration_version`, `accepted: true` | All required steps COMPLETED, verifications valid |

`MOBILE_VERIFICATION`, `BUSINESS_PAN`, `EMAIL_VERIFICATION`, `SIGNATORY_KYC`, `BANK_ACCOUNT` and `AGREEMENT_ESIGN` are completed by their dedicated resources below, not by A8.

### 3.3 Verifications

| # | Method + path | Auth | Request | Response | Errors | State | Idempotency | Rate limit |
|---|---|---|---|---|---|---|---|---|
| A12 | `POST /v1/onboarding/applications/{id}/pan-verifications` 🔁 | session + `If-Match` | `{ pan, document_id? }` | `200 { verification_id, status, pan_masked, name_as_per_pan, holder_type }` + snapshot; `202` if pending | 409 `PAN_ALREADY_REGISTERED`, 422 `VERIFICATION_FAILED` / `PAN_HOLDER_TYPE_NOT_ALLOWED`, 423, 504 | `BUSINESS_PAN` → COMPLETED / FAILED / PENDING_EXTERNAL | key → provider call made once | 5 / application / day |
| A13 | `POST /v1/onboarding/applications/{id}/email-verifications` 🔁 | session | `{ email }` | `202 { verification_id, expires_in }` | 422, 429 | `EMAIL_VERIFICATION` → IN_PROGRESS | key | 5 / h |
| A14 | `POST /v1/onboarding/applications/{id}/email-verifications/{verification_id}/confirm` | session | `{ otp }` | `200 snapshot` | 401, 410, 429 | → COMPLETED | single-use | 5 attempts |
| A15 | `POST /v1/onboarding/applications/{id}/kyc-sessions` 🔁 | session + `If-Match` | `{ method: "DIGILOCKER", consent_version }` | `201 { session_id, redirect_url, expires_at }` | 409, 423 | `SIGNATORY_KYC` → PENDING_EXTERNAL | key returns the same open session | 5 / day |
| A16 | `GET /v1/onboarding/applications/{id}/kyc-sessions/{session_id}` | session | — | `200 { status, result?: { name, dob_year?, aadhaar_masked, name_match_score } }` | 404 | may finalise from provider status | safe | 30 / min |
| A17 | `POST /v1/onboarding/applications/{id}/bank-accounts` 🔁 | session + `If-Match` + OTP re-auth | `{ bank_code, bank_id?, account, ifsc, proof_document_id }` | `202 { bank_account_id, status: "PENDING_EXTERNAL" }` | 422 (per-bank length, IFSC), 423 | `BANK_ACCOUNT` → PENDING_EXTERNAL | key → single penny-drop | 3 / application |
| A18 | `GET /v1/onboarding/applications/{id}/bank-accounts/{bank_account_id}` | session | — | `200 { status, account_masked, beneficiary_name_returned?, name_match_score? }` | — | — | safe | 30 / min |
| A19 | `GET /v1/onboarding/reference/banks` | session | — | bank master (label, value, `dependent_params` with `length_min` / `length_max`, `bank_id`), same shape the `AddBankAccountStep` already consumes | — | — | cacheable 24 h | — |
| A20 | `GET /v1/onboarding/reference/states` | session | — | state list (as EPS `387`) | — | — | cacheable | — |

### 3.4 Documents

| # | Method + path | Auth | Request | Response | Errors | State | Idempotency | Rate limit |
|---|---|---|---|---|---|---|---|---|
| A21 | `POST /v1/onboarding/applications/{id}/documents` 🔁 | session | `{ doc_type, file_name, mime_type, size_bytes, sha256 }` | `201 { document_id, upload: { url, method: "PUT", headers, expires_at } }` | 413, 415, 409 (doc type not applicable) | document `UPLOAD_PENDING` | key | 30 / h |
| A22 | `POST /v1/onboarding/applications/{id}/documents/{document_id}/complete` 🔁 | session | `{}` | `202 { status: "SCANNING" }` | 409 (object missing / size or hash mismatch), 410 | → `SCANNING` | key | — |
| A23 | `GET /v1/onboarding/applications/{id}/documents` | session | — | list: `{ document_id, doc_type, status, version, reject_reason_code?, uploaded_at }` | — | — | safe | — |
| A24 | `GET /v1/onboarding/applications/{id}/documents/{document_id}/download` | session | — | `302` to a presigned GET (60 s) | 404, 409 (not clean) | — | safe | 30 / h |
| A25 | `DELETE /v1/onboarding/applications/{id}/documents/{document_id}` | session + `If-Match` | — | `204` | 403 after submit | → `SUPERSEDED` (soft) | natural | — |

### 3.5 White label

| # | Method + path | Auth | Request | Response | Errors | Rate limit |
|---|---|---|---|---|---|---|
| A26 | `GET /v1/onboarding/subdomains/availability?label=` | session | — | `200 { label, available, reason?, suggestions[] , default_url }` | 422 | 30 / min |

Reservation happens on `WHITE_LABEL_SETUP` submit (A8). It is atomic with the uniqueness check (unique "active-only" key on `domain_reservation`, DATABASE_DESIGN.md §5.1), so two applicants cannot both win.

### 3.6 Agreement and e-sign

| # | Method + path | Auth | Request | Response | Errors | State | Idempotency |
|---|---|---|---|---|---|---|---|
| A27 | `POST /v1/onboarding/applications/{id}/agreements` 🔁 | session + `If-Match` | `{}` | `201 { agreement_id, template_version, preview_url (presigned, 60 s), content_sha256 }` | 409 `PREREQUISITE_INCOMPLETE` | agreement `GENERATING → READY` | key; returns the current agreement if content unchanged |
| A28 | `POST /v1/onboarding/applications/{id}/agreements/{agreement_id}/esign-sessions` 🔁 | session + OTP re-auth | `{ platform: "web" \| "android" }` | `201 { esign_request_id, provider_pipe, signing_url, document_id, expires_at }` (`provider_pipe` keeps the existing `pipe` values 0 Digio, 1 Karza, 2 Signzy, 3 Leegality so `services/esign/getProvider()` works unchanged) | 409 (already signed → `ALREADY_SIGNED`), 410 | `READY → SIGNING` | key; returns the open session if unexpired |
| A29 | `GET /v1/onboarding/applications/{id}/agreements/{agreement_id}` | session | — | `200 { status, signed_at?, signer_name_returned?, signed_document_available }` | — | may finalise from the provider status API | safe |

The UI calls A29 when the provider window closes, when `/redirect` posts `STATUS_UPDATE`, or on the Android `LEEGALITY_ESIGN_RESPONSE`. **Only the server sets `SIGNED`.**

### 3.7 Status page (post-submit)

| # | Method + path | Response |
|---|---|---|
| A30 | `GET /v1/onboarding/applications/{id}/timeline` | Applicant-safe event list (submitted, under review, correction requested, approved, provisioning, go-live tasks, activated) |

---

## 4. Staff APIs (`/v1/admin`)

All staff APIs require OIDC auth + MFA claim (`amr` contains `mfa`). Permission per row. Every call is audited.

| # | Method + path | Permission | Request | Response | State transition | Idempotency |
|---|---|---|---|---|---|---|
| S1 | `GET /v1/admin/applications?status=&assigned_to=&q=&cursor=` | `onboarding.read` | filters (`q` searches company name, masked mobile, `onboarding_id`; PAN search by exact value → hash lookup) | page of summaries | — | safe |
| S2 | `GET /v1/admin/applications/{id}` | `onboarding.read` | — | full case: steps, verifications (masked), documents, review history, duplicates / risk flags | — | safe |
| S3 | `POST /v1/admin/applications/{id}/claim` 🔁 | `onboarding.review` | `{}` | case | T4 | key |
| S4 | `POST /v1/admin/applications/{id}/release` 🔁 | `onboarding.review` | `{ reason }` | case | `UNDER_REVIEW → SUBMITTED` | key |
| S5 | `POST /v1/admin/applications/{id}/documents/{doc}/verify` 🔁 | `onboarding.review` | `{ note? }` | document | doc `PENDING_REVIEW → VERIFIED` | key |
| S6 | `POST /v1/admin/applications/{id}/documents/{doc}/reject` 🔁 | `onboarding.review` | `{ reason_code, message }` | document | `→ REJECTED` | key |
| S7 | `POST /v1/admin/applications/{id}/verifications/{ver}/decide` 🔁 | `onboarding.review` | `{ decision: ACCEPT \| REJECT, reason_code }` | verification | `MANUAL_REVIEW → SUCCEEDED / FAILED` | key |
| S8 | `POST /v1/admin/applications/{id}/corrections` 🔁 | `onboarding.review` | `{ step_keys[], reason_code, message, due_in_days? }` | case | T5 | key |
| S9 | `POST /v1/admin/applications/{id}/recommendations` 🔁 | `onboarding.review` | `{ recommendation: APPROVE \| REJECT, note }` | case | (records the maker step) | key |
| S10 | `POST /v1/admin/applications/{id}/approve` 🔁 | `onboarding.approve` (≠ recommender) | `{ note }` | case | T7 → T10 | key |
| S11 | `POST /v1/admin/applications/{id}/reject` 🔁 | `onboarding.reject` | `{ reason_code, applicant_message, internal_note }` | case | T8 | key |
| S12 | `POST /v1/admin/applications/{id}/holds` / `DELETE …/holds` 🔁 | `onboarding.hold` | `{ reason_code }` | case | T9 / release | key |
| S13 | `POST /v1/admin/applications/{id}/pii-reveal` 🔁 | `pii.reveal` | `{ field: PAN \| ACCOUNT \| MOBILE \| EMAIL, purpose }` | `{ value }`, valid for one response only | — (audit `PII_REVEALED`) | key |
| S14 | `GET /v1/admin/applications/{id}/provisioning` | `onboarding.provision` | — | run + steps | — | safe |
| S15 | `POST /v1/admin/applications/{id}/provisioning/retry` 🔁 | `onboarding.provision` | `{}` | run | T13 | key |
| S16 | `GET /v1/admin/tenants/{tenantId}/go-live-tasks` | `tenant.read` | — | tasks | — | safe |
| S17 | `PATCH /v1/admin/tenants/{tenantId}/go-live-tasks/{task_key}` | `tenant.golive` | `{ status, external_ref?, note? }` | task | — | natural |
| S18 | `POST /v1/admin/applications/{id}/activate` 🔁 | `tenant.activate` (≠ approver, OQ-O3) | `{}` | case | T14 (409 + failed gate checks if not ready) | key |
| S19 | `POST /v1/admin/leads` 🔁 / `GET /v1/admin/leads` / `PATCH /v1/admin/leads/{id}` | `lead.write` / `lead.read` | lead fields | lead | lead machine | key |
| S20 | `POST /v1/admin/leads/{id}/invites` 🔁 | `lead.write` | `{ channel: SMS \| EMAIL }` | `{ invite_expires_at }` | `→ INVITED` | key |
| S21 | `GET /v1/admin/audit?entity_type=&entity_id=` | `audit.read` | — | events | — | safe |
| S22 | `POST /v1/admin/applications/{id}/expire-now` / `extend` 🔁 | `onboarding.ops` | `{ days }` | case | T15 / extend `expires_at` | key |

---

## 5. Tenant APIs (post-activation, PROPOSED, OQ-T9)

| # | Method + path | Auth | Purpose |
|---|---|---|---|
| T1 | `GET /v1/tenants/{tenantId}/domains` | EPS token of a Local Admin of `tenantId` | List domains |
| T2 | `POST /v1/tenants/{tenantId}/domains` 🔁 | same | Add a custom domain → `{ txt_name, txt_value, cname_target }` |
| T3 | `POST /v1/tenants/{tenantId}/domains/{domainId}/verify` 🔁 | same | Trigger a re-check |
| T4 | `POST /v1/tenants/{tenantId}/branding-versions` 🔁 | same | Propose new logo / app name (goes to staff approval) |

`tenantId` in the path must equal the tenant in the token, otherwise `404`. The host header is ignored for authorization.

---

## 6. Webhooks

| # | Method + path | Verification | Behaviour |
|---|---|---|---|
| W1 | `POST /v1/webhooks/leegality` | Provider HMAC / signature (**EXTERNAL API DETAILS REQUIRED**) + timestamp tolerance 5 min | Insert into `webhook_event` (unique provider event id) → `200` fast → async processing → confirm via the provider status API → `SIGNED` |
| W2 | `POST /v1/webhooks/karza` | as above | e-sign or DigiLocker callbacks |
| W3 | `POST /v1/webhooks/signzy`, `/digio` | as above | e-sign |
| W4 | `POST /v1/webhooks/bank` | as above | Penny-drop result (if async) |
| W5 | `POST /v1/webhooks/storage` | Cloud event signature | Object-created → start the scan |

Rules:
- respond `200` within 2 s,
- duplicates → `200` with no effect,
- out-of-order events are resolved by provider timestamps + the state machine (a `SIGNED` never regresses to `SIGNING`),
- unknown references → stored and alerted, `200`.

---

## 7. Idempotency and duplicate prevention

| Operation | Keys / constraints | Behaviour on repeat |
|---|---|---|
| Onboarding creation | Unique non-terminal application per applicant; OTP single-use | Resume the existing one |
| OTP send | `Idempotency-Key` + 30 s per-mobile window | Same `otp_ref` returned |
| PAN verification | `Idempotency-Key` → `verification.request_idempotency_key`; provider called once per key | Stored result returned; no extra provider charge |
| KYC session | Open, unexpired session reused | Same `redirect_url` |
| Document submission | `Idempotency-Key`; `sha256` + `doc_type` dedup per application | Same `document_id` |
| Bank verification | `Idempotency-Key`; `(application_id, account_hash, ifsc)` attempt cache | Same result; no second penny-drop |
| Agreement creation | `content_sha256` unchanged → same agreement | Same `agreement_id` |
| e-sign | Open session reused; `UNIQUE(provider_document_id)` | Same signing URL |
| Approval / rejection | State guard (only from `UNDER_REVIEW`) + key | `409` or replay |
| Tenant creation | `UNIQUE(application_id)`, `UNIQUE(eps_org_id)`, step key `prov:{app}:{step}`, EPS lookup-before-create | Resume; never a second tenant |
| Local Admin activation | State guard + key | Replay |
| Notifications | `UNIQUE(template_code, aggregate_id, dedup_key)` | Not resent (fixes the 7-duplicate-email defect) |
| Webhooks | `UNIQUE(provider, provider_event_id)` (or payload hash when no id) | Ignored |

---

## 8. Error and edge-case behaviour

| # | Case | Expected backend behaviour |
|---|---|---|
| E1 | Duplicate mobile (non-terminal application exists) | OTP verify **resumes** it; no new application |
| E2 | Mobile belongs to an active Local Admin | After OTP: `409 DUPLICATE_APPLICATION`, generic message "already registered"; audit `DUPLICATE_LOCAL_ADMIN_ATTEMPT` |
| E3 | Mobile belongs to an existing agent / user in EPS | Allowed or blocked per OQ-B6; default: allowed, flagged for review |
| E4 | Duplicate email | Allowed (shared mailboxes), flagged when it matches another tenant's admin email |
| E5 | Duplicate organisation (PAN already in a non-terminal or active application) | `409 PAN_ALREADY_REGISTERED`; staff see the linked application |
| E6 | Duplicate onboarding via two tabs (two "start" calls) | Unique index + resume → same `onboarding_id` |
| E7 | OTP expiry | `410 OTP_EXPIRED`; resend allowed after the gap |
| E8 | OTP retry limit | `429 OTP_LOCKED` + `retry_after` (15 min, ASSUMPTION); audit |
| E9 | PAN mismatch (name-as-per-PAN ≠ entered name) | Step `COMPLETED` with `NAME_MISMATCH` flag → reviewer decides (S7); below a hard threshold → `FAILED` |
| E10 | KYC failure (DigiLocker incomplete / mobile mismatch) | Step `FAILED` with code; new session allowed until max attempts → `BLOCKED` |
| E11 | Bank verification failure | `FAILED` (invalid) or `MANUAL_REVIEW` (name mismatch); max 3 attempts |
| E12 | Provider timeout | `PENDING_EXTERNAL`; reconciliation job polls the status API (1, 5, 15, 60 min); UI polls; never double-calls thanks to the idempotency key |
| E13 | E-sign failure | e-sign `FAILED`; retry generates a new provider document; old one is superseded |
| E14 | Duplicate webhook | Ignored by the unique constraint |
| E15 | Out-of-order webhook (e.g. "opened" after "signed") | Ignored if it would regress state; stored for audit |
| E16 | User refresh / browser closed mid-step | State is server-side; `PENDING_EXTERNAL` resolved by webhook / reconciliation |
| E17 | Resume after several days | Snapshot shows expired verifications (`valid_until`) as `IN_PROGRESS` with `VERIFICATION_EXPIRED`; reminders were sent |
| E18 | Multiple tabs editing | `If-Match` → `412` + fresh snapshot |
| E19 | Document upload failure (network) | Upload URL can be re-requested; `UPLOAD_PENDING` docs auto-abandoned after expiry |
| E20 | Document rejected by reviewer | Correction request reopens `DOCUMENTS`; applicant uploads a new version (old → `SUPERSEDED`) |
| E21 | CRM failure | Outbox retries with backoff; dead-letter after 24 h → ops alert; **does not** block approval or activation |
| E22 | Email / SMS failure | Retries + fallback channel; notification status visible to staff |
| E23 | Custom domain already exists (another tenant or another org in org-meta) | `409 DOMAIN_TAKEN`; staff can investigate ownership via TXT challenge |
| E24 | Sub-domain already exists | `409 SUBDOMAIN_TAKEN` + suggestions; reservation is atomic |
| E25 | Branding failure (logo invalid / unsafe SVG) | `422`; SVG sanitised or rejected; publish failure at activation → retry; activation can proceed with default branding if allowed (OQ-P8) |
| E26 | Tenant provisioning failure | `PROVISIONING_FAILED` with the failed step; ops retry resumes; nothing is ever half-active |
| E27 | Partial activation failure (local commit ok, EPS activation call fails) | Outbox retries until success; tenant shows `ACTIVE` locally but the login gate in EPS remains closed until confirmed; alert after 3 failures; **no** user-facing "live" message until EPS confirms |
| E28 | Applicant withdraws during review | Allowed only before claim; after claim, the reviewer handles it (reject with reason `WITHDRAWN_BY_APPLICANT`) |
| E29 | Consent withdrawn | Application → `WITHDRAWN`; processing stops; retention rules apply |
| E30 | Reviewer tries to approve own recommendation | `403 MAKER_CHECKER_VIOLATION` |

# Executive Summary

> **Phase 0 deliverable:** requirements, architecture and implementation blueprint. No code, packages, migrations or integrations were created. The frontend was not modified. The full documents follow this summary (also in `wlc-webapp/docs/`).
>
> **Revised 14 October 2026:** the team chose **MySQL 8.4** as the database, and the backend stack moved from TypeScript + NestJS to **Java 25 + Spring Boot 4**. The journey, state machine, APIs and security rules are unchanged. Sections 7–9 and 12 below show what changed.

## 1. Frontend findings

- `wlc-webapp` is a **Next.js 14** white-label tenant app. It serves existing tenants by sub-domain or custom domain (org-metadata lookup, interaction `677`). It calls the external EPS backend through an interaction-ID envelope and a "tf-req" REST tunnel.
- It has **no Local Admin / tenant onboarding**. `/signup` is agent onboarding into an existing tenant (`src/features/onboarding`). The "Enterprise Partner" role exists but is hidden.
- **Reusable pieces:**
  - a config-driven step pipeline,
  - `BusinessDetailsStep`, `AddBankAccountStep`, `DigilockerRedirectionStep`, `VideoKycStep`, `SignAgreementStep`,
  - e-sign providers (Leegality / Karza / Signzy / Digio),
  - the admin Agreement Signing screen (email OTP, address, signature),
  - the theme configuration.
- **Risks found:**
  - e-sign success is trusted from the client and from un-checked `postMessage`,
  - a UAT DigiLocker path is hard-coded,
  - fallback GPS coordinates are sent as if real,
  - skips and retry state are not persisted,
  - `client_ref_id = Date.now()` is not an idempotency key,
  - uncommitted local work adds hard-coded test identities.

## 2. Business findings

- Today's process spans **Eko Connect** (mobile → Enterprise → PAN → White Label + app name + `*.finstore.app` sub-domain → e-sign) → the **Go-Live staff tool** (re-verify mobile / email / PAN, confirm) → **the admin's own domain** (business details, signature, a second e-sign) → **CS manual review + CRM upload** → **manual DLT, logo, Play Store and domain tickets**.
- The whiteboard targets a simpler **6-page journey** (mobile, choose option, PAN, Aadhaar, live photo, agreement), "similar to SBI", built to **"bank grade"**, following RBI guidance and the DP Act.

## 3. Conflicts and gaps

- **Conflicts:**
  - the tenant and sub-domain exist **before** approval,
  - the agreement is signed **twice** with two providers,
  - company PAN vs admin PAN (the sample is an individual PAN),
  - Aadhaar is verified only inside e-sign, versus as a separate step,
  - staff verify and confirm **on behalf of** the customer,
  - `finstore.app` vs `elokademo.in`,
  - three different org-name-change mechanisms.
- **Gaps:** consent, duplicate detection, a correction loop, expiry, maker-checker, risk screening, provisioning failure handling, activation criteria, notifications.
- **Defect evidence:** one "Onboarding Complete" sent 7 duplicate emails.

## 4. Confirmed journey

Lead → mobile OTP → Enterprise + White Label → PAN → app name + sub-domain → agreement + Aadhaar e-sign → staff re-verification → business details + signature (+ second signing) → final confirmation → CS review + CRM → go-live tasks (DLT, logo, Play Store, domain).

## 5. Recommended journey (new steps marked PROPOSED / RECOMMENDED)

1. Lead + invite *(PROPOSED)*
2. Mobile OTP → `onboarding_id`
3. Consent *(PROPOSED)*
4. Program selection
5. Business PAN + dedup *(PROPOSED dedup)*
6. Business details + signature
7. Email (applicant-side, *PROPOSED move*)
8. Signatory KYC via DigiLocker
9. Live photo (*face match PROPOSED*)
10. Documents
11. Bank (open question)
12. White-label setup (sub-domain **reserved**)
13. Declaration (applicant-side, *PROPOSED move*)
14. **One** agreement e-sign → SUBMITTED
15. Review + correction loop *(PROPOSED)*
16. Maker-checker approval *(PROPOSED)*
17. Automated provisioning saga *(PROPOSED)*
18. Go-live checklist *(PROPOSED tracking)*
19. Activation gate + welcome *(PROPOSED)*

## 6. State machine

- **Application states:** `IN_PROGRESS` → `SUBMITTED` → `UNDER_REVIEW` → (`CORRECTION_REQUIRED` ↺ | `ON_HOLD` | `REJECTED`) → `APPROVED` → `PROVISIONING` (↔ `PROVISIONING_FAILED`) → `GO_LIVE_PENDING` → `ACTIVE`.
- **Terminal states:** `WITHDRAWN`, `EXPIRED`, `REJECTED`, `ACTIVE`.
- **Separate machines:** steps, lead, verification, e-sign, document, provisioning, domain, tenant and Local Admin.
- **Rule:** no Local Admin can become active unless the whole chain has completed.

## 7. Architecture

- A new **ELOKA Onboarding Service**, built as a **modular monolith** (api + worker processes) in **Java 25 + Spring Boot 4 + Spring Modulith**. It sits beside EPS and integrates through ports and adapters (EPS-first). EPS stays the system of record for live tenants and users.
- **Rejected:** microservices (over-engineered for this volume), serverless (long sagas), and extending EPS (code unavailable; owner decision OQ-T5).
- **Why Java + Spring Boot** (changed from TypeScript + NestJS on 14 October 2026): the service is bank-grade, compliance-heavy and long-lived. Spring gives mature security, transactions and a built-in outbox; Java is the banking standard in India. The frontend keeps type safety through an OpenAPI-generated client.

## 8. Technology stack

| Area | Choice |
|---|---|
| Language / framework | **Java 25 (LTS) + Spring Boot 4 + Spring Modulith**, Gradle build |
| Database / access | **MySQL 8.4 LTS** (InnoDB, utf8mb4, own database, not inside `ekodb`) — **team decision**; Spring Data JPA (Hibernate); **Flyway** migrations |
| Validation / API | Jakarta Bean Validation; REST + OpenAPI (springdoc); frontend types + Zod generated with **Orval** |
| Async | **JobRunr** (jobs, retries, schedules) + **Spring Modulith outbox**, both on MySQL (no Redis / Kafka) |
| Applicant auth | OTP + server-side sessions (**Spring Session JDBC**) |
| Staff auth | **Spring Security OIDC** SSO (Google Workspace or Keycloak — open) + MFA + RBAC with maker-checker |
| Integrations | Spring `RestClient` + **Resilience4j** (timeouts, retries, circuit breakers) |
| Encryption | **Google Tink** + AWS KMS envelope encryption; HMAC blind indexes |
| Storage | S3-compatible storage in an Indian region with KMS, presigned URLs, **ClamAV**, Object Lock |
| Observability | Logback JSON with masking, **Micrometer + OpenTelemetry**, Sentry |
| Testing | JUnit 5, Testcontainers (MySQL), WireMock, Pact JVM, ArchUnit, Playwright (E2E) |
| Delivery | Docker, GitHub Actions (CodeQL, Sonar, gitleaks, OWASP Dependency-Check, Trivy) |
| Frontend | Existing **Next.js 14** (`wlc-webapp`) for applicant screens; a small Next.js staff console |

## 9. Database

- **Engine:** MySQL 8.4. PostgreSQL-only features from the first draft are replaced:
  - "unique only while active" rules → **stored generated columns + unique indexes**,
  - Row-Level Security → **repository scoping + cross-tenant tests + per-role DB users**,
  - `JSONB` → `JSON`; UUIDv7 stored as `BINARY(16)`; times as `DATETIME(6)` UTC.
- **Pre-tenant aggregate:** `onboarding_application` + `onboarding_step` + verifications / documents / bank / agreement / e-sign / branding draft / domain reservation.
- **Staff tables:** review / corrections / provisioning.
- **Tenant tables:** tenant / domains / branding versions / Local Admins / go-live tasks.
- **Infrastructure tables:** audit (hash chain), outbox, job queue, idempotency, webhook inbox, call log.
- **Data handling:** PAN, account, mobile and email are encrypted with HMAC blind indexes; **no Aadhaar number is stored**.

## 10. APIs

- **Applicant:** OTP / session, snapshot + generic steps (`If-Match`), PAN, email, KYC sessions, bank, documents (presigned), sub-domain availability, agreement + e-sign sessions, timeline.
- **Staff:** queue, claim, document / verification decisions, corrections, recommend / approve / reject, holds, PII reveal, provisioning retry, go-live tasks, activation, leads, audit.
- **Tenant:** custom domains, branding versions.
- **Webhooks:** per provider.
- **Common rules:** `Idempotency-Key` on every side-effect POST, problem+json errors.

## 11. Integrations

EPS (provisioning + PAN `205`, e-sign `287` / `293`, bank `454`, OTP `553` / `554` / `555`, org `774` / `677`, DigiLocker via Karza), the org-metadata service, SMS / email, object storage, ClamAV, CRM, DNS / TLS. DLT and Play Store stay manual go-live tasks. Every unknown contract is marked **EXTERNAL API DETAILS REQUIRED**.

## 12. Security

- **Tenant isolation: two walls plus safety nets** (policy, repository scope; then cross-tenant tests, ArchUnit rules and per-role MySQL users). MySQL has no Row-Level Security, so the first draft's third, database-level wall is replaced by these safety nets — an accepted risk of the MySQL choice. The domain never authorizes.
- OTP hardening; sessions kept out of JavaScript.
- Field-level encryption; masked PII with audited reveal; maker-checker.
- Signed webhooks with re-confirmation; private document pipeline with scanning.
- Redaction allow-list logging; an append-only, hash-chained audit (insert-only DB grants) with a final event catalogue.

## 13. Edge cases

30 cases specified (`API_SPECIFICATION.md` §8), including:

- duplicate mobile / PAN / sub-domain / domain,
- OTP expiry and lockout,
- provider timeouts,
- duplicate and out-of-order webhooks,
- multi-tab edits,
- resume after days,
- document rejection,
- CRM / email failure,
- provisioning and partial-activation failure.

## 14. Implementation plan

0. Architecture
1. Foundation (audit, outbox, crypto, idempotency, observability first) — Spring Boot + MySQL + Flyway skeleton
2. Identity
3. Onboarding core + state machine
4. PAN
5. Email / KYC / live photo
6. Documents / bank
7. White-label + e-sign + submit (vertical slice)
8. Review console
9. Provisioning saga
10. Go-live + CRM + activation
11. Custom domains
12. Hardening + production readiness

A frontend track runs in parallel.

## 15. Open questions

60+ questions grouped as Business, Product, Technical, Integration, Security, Compliance and Operations (`OPEN_QUESTIONS.md`).

**Closed on 14 October 2026:** OQ-T1 database → **MySQL 8.4**. The backend language is now **Java + Spring Boot**.

The most blocking:

- **OQ-T2** EPS provisioning APIs
- **OQ-C1** Aadhaar method / storage
- **OQ-T5** backend owner (needs a team with Java / Spring skills)
- **OQ-B5** whose PAN
- **OQ-B10** one agreement or two
- **OQ-P4 / P5 / P6** live photo, documents, bank
- **OQ-T8** staff login provider (Google Workspace or Keycloak)

**Self-review score: 8 / 10.** The missing points depend on EPS contracts that are not yet known. Moving to MySQL removes the database-level isolation wall; the plan compensates with tests and DB grants.

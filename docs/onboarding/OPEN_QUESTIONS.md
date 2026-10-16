# ELOKA Local Admin Onboarding — Open Questions

> Status: **DRAFT for architecture review** · **Revised 14 October 2026:** Java 25 + Spring Boot 4, MySQL 8.4 · Every question here is unresolved unless marked **CLOSED**. **None has been answered with an assumption.**
> Decisions so far (14 October 2026): database = **MySQL 8.4** (OQ-T1 closed); backend language = **Java 25 + Spring Boot 4** (see ARCHITECTURE.md §5; OQ-T5 now asks only who owns it).
> Where a working assumption was needed to keep the design moving, it is marked ASSUMPTION in the other docs and **listed here**, so it can be confirmed or replaced.
>
> **Since P7 (16 Oct 2026)** the assumptions the applicant API depends on are settings, not code: `eloka.onboarding` in `server/src/main/resources/application.yaml` (idle expiry OQ-B9, live photo OQ-P4, bank OQ-P6, PAN holder types OQ-B5, required documents OQ-P5, consent/terms/declaration versions). Answering a question = changing that setting. Name-match threshold (OQ-C3) is `NameMatch.MATCH_THRESHOLD` (80).
> "Blocks" = the implementation phase (IMPLEMENTATION_PLAN.md) that cannot start until the question is answered.

---

## Business

| ID | Question | Why it matters | Working assumption (if any) | Blocks |
|---|---|---|---|---|
| OQ-B1 | Can anyone self-sign-up for White Label, or only invited / qualified leads? | Spam applications, review load, invite design | Both allowed; invite pre-links the lead | Phase 3 |
| OQ-B2 | Which EPS user type does a Local Admin get: `ENTERPRISE_PARTNER_ADMIN (23)`, `ORG_ADMIN (24)`, or something else (the admin mock uses `22`)? Is `is_org_admin` set? | Provisioning, menus, permissions in `wlc-webapp` | — | Phase 9 |
| OQ-B3 | Is the CRM or this service the system of record for leads? Which CRM? | Lead module scope, sync direction | — | Phase 3 |
| OQ-B4 | Which legal entity types are supported for Local Admins (Pvt Ltd, LLP, Partnership, Sole Proprietorship, Public Ltd, Trust / Society, OPC…)? | Step policy, document list, PAN rules | The frontend's 4 types | Phase 3 |
| OQ-B5 | Whose PAN is verified: the **company / firm's** PAN or the **admin's** personal PAN? (Connect says company; doc text says admin; the doc's sample PAN is an individual `P`-type.) Which PAN holder types are allowed per entity type? | Legal identity of the contracting party | Entity PAN; individual PAN only for proprietorships | Phase 4 |
| OQ-B6 | Can one PAN / legal entity own more than one tenant? Can a mobile that is already an Eloka agent / distributor (in any tenant) become a Local Admin? Can one bank account serve several tenants? | Duplicate rules | One active tenant per PAN; others flagged | Phase 4 |
| OQ-B7 | After rejection, can the same PAN reapply? After how long? Is any reason permanently disqualifying? | Rejection policy | New application allowed; reviewer sees history | Phase 8 |
| OQ-B8 | Do we accept moving tenant / sub-domain creation to **after** approval (today it exists before CS verification)? | Core lifecycle change | Yes (recommended) | Phase 9 |
| OQ-B9 | Expiry: how long can an application stay idle? Correction window? Reminder cadence? | Data retention, sub-domain squatting | 30 days idle; 15 days correction; reminders on days 7 / 14 / 21 | Phase 3 |
| OQ-B10 | Is there **one** Local Admin agreement, or two (today it is signed in Connect via Leegality **and** again on the admin domain via Karza)? Who owns the template? Which Eko entity is the counter-party ("Grey Swift Private Limited" appears as the e-sign requester)? Does the agreement need a counter-signature by Eko? | Agreement module, e-sign flow | One agreement, signed once by the applicant's signatory | Phase 7 |
| OQ-B11 | If the applicant picks **Partner APIs** instead of White Label, what happens: out of scope, or the same engine with a different step policy? | Program scope | Out of scope; hand off to the existing API-partner flow | Phase 3 |
| OQ-B12 | If the Aadhaar-linked mobile of the signatory differs from the onboarding mobile (today's `2443` error), is that acceptable for a business signatory? | KYC rules | Not acceptable (as today) | Phase 5 |
| OQ-B13 | What happens commercially after activation: plan / pricing selection, security deposit, invoicing setup? Are they part of onboarding? | Possible extra steps (the agent flow has an `ACTIVATION_PLAN` step id 13 that is unused) | Not part of onboarding | — |
| OQ-B14 | Which products (DMT, AePS, BBPS, QR, PG, Travel, Insurance) are enabled by default for a new tenant, and does product enablement need separate approvals or agreements per product ("Backend respect to products" on the notes)? | Default configuration in provisioning | Default set from config | Phase 9 |

## Product

| ID | Question | Why it matters | Working assumption | Blocks |
|---|---|---|---|---|
| OQ-P1 | Is the recommended step order acceptable (data and verification first, agreement last), versus today's (agreement + Aadhaar early)? | Journey | Recommended order | Phase 3 |
| OQ-P2 | Where does the onboarding UI live: a new route group in `wlc-webapp` on a platform host, Eko Connect, or a new small app? Where does the staff console live? | Frontend plan; `_app.tsx` 404s hosts without an org | New small app or `wlc-webapp` route group; separate staff console | Phase 2 |
| OQ-P3 | Should the Local Admin set a transaction PIN (`SECRET_PIN` step) during onboarding or after activation? | Scope | After activation (existing in-app flow) | — |
| OQ-P4 | Is the **live photo** mandatory for Local Admins (whiteboard yes; doc no)? Is face match against the Aadhaar photo required? Is location capture required? | KYC design, provider need | Configurable; on by default | Phase 5 |
| OQ-P5 | Exact **document list** per entity type (COI, MOA / AOA, partnership deed, board resolution / authority letter, GST certificate, address proof, cancelled cheque…)? Are GSTIN / CIN / LLPIN fields required and verified? | Documents step, review checklist | — | Phase 6 |
| OQ-P6 | Is **bank account + verification** mandatory for a Local Admin? What is it used for (settlement, commission payout, refunds)? | Bank step | Mandatory, penny-drop | Phase 6 |
| OQ-P7 | Is the **Google Play app** part of every tenant's go-live? Who owns the developer account and the signing keys: Eko or the partner? | Go-live checklist; security of signing keys | Optional task, tracked | Phase 10 |
| OQ-P8 | Are logo and colours **mandatory before activation**, or can a tenant go live with default branding? Is the logo designed by Eko (the doc mentions a designer ticket) as a tracked task? | Activation gate | Optional; default branding allowed | Phase 7 / 10 |
| OQ-P9 | After activation, who can change the app name, org name, logo, sub-domain? Which need Eko approval? (Today: org-name change = support ticket.) | Change workflows | Name / logo need approval; colours self-serve | Phase 10+ |
| OQ-P10 | Does the agreement print the brand name / sub-domain (so changing them requires re-signing)? | Invalidation rules | No | Phase 7 |
| OQ-P11 | Should Eko staff be able to fill in the application **on behalf of** a customer (assisted onboarding, like today's Go-Live tool)? If yes, how is customer consent captured? | Assisted mode, consent validity | No; staff assist via review / corrections only | Phase 8 |
| OQ-P12 | Is Google sign-in (offered on the Connect login) needed for applicants, or mobile OTP only? | Identity scope | Mobile OTP only | Phase 2 |
| OQ-P13 | Languages for the onboarding UI and consent text (the app has i18n labels)? | Content | English first | Phase 3 |

## Technical

| ID | Question | Why it matters | Working assumption | Blocks |
|---|---|---|---|---|
| OQ-T1 | ~~PostgreSQL or MySQL for the new service?~~ **CLOSED 14 October 2026: MySQL 8.4** (team decision). Follow-up: which MySQL estate hosts it (RDS or Eko's existing servers), and who is the DBA owner? | DB choice | MySQL 8.4, own database | Phase 1 |
| OQ-T2 | **EPS APIs for provisioning:** create org, create admin user for an org, activate / deactivate both, bind sub-domain / domain in org-meta, set logo / app name, lookup by PAN / mobile. Are they idempotent (`client_ref_id`)? Is there a service-to-service auth mode? | Provisioning automation | — | **Phase 9** |
| OQ-T3 | Do the platform zones have **wildcard DNS + wildcard TLS** today? Which edge / LB terminates TLS for custom domains, and can it automate certificates (ACME / managed certs)? | Sub-domain and domain activation | Wildcard exists | Phase 9 / 11 |
| OQ-T4 | Production platform zone(s): `finstore.app` (doc), `elokademo.in` (frontend env), others? Per program or per region? | Sub-domain reservation | Configurable list | Phase 7 |
| OQ-T5 | **Who owns** the backend? The language is now **Java 25 + Spring Boot 4** (14 October 2026), so the owning team needs Java skills. Is that the EPS team, a new backend team, or the web team? | Staffing and code ownership | A team with Java / Spring experience | Phase 1 |
| OQ-T6 | Hosting target and platform: AWS / GCP / DigitalOcean / on-prem racks? Kubernetes available? Existing log / metrics stack? Local dev topology (the WIP `.env.development` points the API at `localhost:3004`, which is also `dev.https`'s port) | Deployment design | Containers on a managed platform | Phase 1 |
| OQ-T7 | Object storage + KMS: does the chosen provider support SSE with customer-managed keys, versioning and **Object Lock** in an Indian region (verify DigitalOcean Spaces if that is the default, since it is in the CSP allow-list)? | Document security | AWS S3 `ap-south-1` meets all | Phase 1 |
| OQ-T8 | Staff IdP: **Google Workspace or Keycloak** (or another)? Can it enforce MFA and expose group → role mapping? | Staff auth (Spring Security OIDC) | Google Workspace OIDC if Eko staff already use it | Phase 2 |
| OQ-T9 | How do post-activation tenant users authenticate to this service (custom-domain APIs)? Can EPS tokens be validated here (JWKS / introspection)? | Tenant APIs | Token introspection via EPS | Phase 11 |
| OQ-T10 | How is the org-metadata cache purged across all `wlc-webapp` instances when a tenant is activated or branding changes (today `/clear_org_cache` per instance, TTL up to 8 h in production)? | Activation visibility | Purge API or short TTL for new tenants | Phase 9 |
| OQ-T11 | Are the uncommitted local changes on `fix/auto-trigger-balance-api-post-login` (Next.js API proxy, interaction-envelope login, hard-coded test identities) going to land? They change the frontend's backend contract. | Frontend adapter design | Treat HEAD as current | FE-2 |

## Integration

| ID | Question | Why it matters | Working assumption | Blocks |
|---|---|---|---|---|
| OQ-I1 | OTP and email: does this service use EPS `553` / `554` / `555` (which `intent_id`?), or its own SMS / email providers? Which transactional email provider replaces Gmail? | Messaging design, DLT templates | EPS for OTP; SES / SMTP for email | Phase 2 |
| OQ-I2 | Which PAN endpoint is approved for business onboarding (EPS `205` vs v3 `/tools/kyc/pan-lite` / `pan-advanced`)? Response schema? Cost per call? | PAN adapter | — | Phase 4 |
| OQ-I3 | Which **e-sign provider** for the Local Admin agreement (Leegality / Karza / Signzy / Digio)? Webhook signature scheme and status API? Through EPS or direct? | Agreement module | Leegality via EPS | Phase 7 |
| OQ-I4 | DigiLocker (Karza) contract: data fields returned, callback support, session expiry, data-retention terms; through EPS or direct? | KYC adapter | Via EPS, polling | Phase 5 |
| OQ-I5 | CRM API: product, auth, object model, document upload limits, rate limits | CRM sync | — | Phase 10 |
| OQ-I6 | Bank verification contract (EPS `454` underlying vendor, sync / async, beneficiary-name field, cost) | Bank adapter | — | Phase 6 |
| OQ-I7 | Liveness / face-match provider (if OQ-P4 says yes) | Live photo | — | Phase 5 |

## Security

| ID | Question | Why it matters | Working assumption | Blocks |
|---|---|---|---|---|
| OQ-S1 | Must the staff console be reachable only from the corporate network / VPN? | Admin surface | Yes (plus SSO) | Phase 8 |
| OQ-S2 | Who may reveal unmasked PAN / account numbers, and must every reveal state a purpose? | PII access | Compliance role only; purpose required. **P8 built:** purpose required and audited; the `APPROVER` role reveals until a compliance role is defined | Phase 8 |
| OQ-S3 | Session lifetimes for applicants (idle / absolute) and the OTP length for applicant login | Auth | 30 min / 12 h; 6 digits | Phase 2 |
| OQ-S4 | Is a CAPTCHA acceptable on OTP send after abuse thresholds (Android WebView compatibility)? | OTP-pumping defence | Yes, adaptive | Phase 2 |
| OQ-S5 | Is a third-party penetration test required before launch, and is there a standard (e.g. CERT-In empanelled auditor)? | Launch gate | Yes | Phase 12 |
| OQ-S6 | Is Google Workspace 2-step verification (enforced by the Workspace admin) enough as staff MFA, or must the server enforce a second factor itself? | Staff console access | Workspace 2SV is enforced for every staff account | P8 (built on this assumption) |

## Compliance

| ID | Question | Why it matters | Working assumption | Blocks |
|---|---|---|---|---|
| OQ-C1 | Aadhaar: which method is permitted for a business signatory (DigiLocker, offline XML, OTP e-KYC via a licensed KUA / sub-AUA)? Can we store masked Aadhaar + name only? Is an Aadhaar Data Vault required for anything we store? Is e-sign Aadhaar OTP alone sufficient KYC? | Legal basis for KYC design | DigiLocker; no Aadhaar number stored | **Phase 5** |
| OQ-C2 | Retention periods per data class: in-progress, withdrawn, expired, rejected, active, terminated tenants; signed agreements; audit logs. Which regulation applies to Eloka Local Admins (RBI KYC directions, PA guidelines, DPDP Act 2023)? | Retention jobs, crypto-shredding | — | Phase 12 |
| OQ-C3 | Name-match thresholds (PAN vs business name, KYC vs signatory, bank beneficiary vs legal name) and validity windows for each verification | Verification rules | Score < threshold → manual review | Phase 4 |
| OQ-C4 | Is sanctions / PEP / negative-list screening required for Local Admins? Which provider? | Risk step | Not in Phase 1; designed as `ON_HOLD` | Phase 8 |
| OQ-C5 | Consent and privacy-notice text, versions and languages (DPDP Act 2023); who signs off? | Consent step | Legal provides | Phase 3 |
| OQ-C6 | Data residency: must all KYC data and documents stay in India? | Hosting region | Yes | Phase 1 |
| OQ-C7 | "Follow all test cases of RBI" (notes): which specific RBI checklist or audit framework does the business mean? | Compliance test plan | — | Phase 12 |

## Operations

| ID | Question | Why it matters | Working assumption | Blocks |
|---|---|---|---|---|
| OQ-O1 | Which team reviews applications (Customer Service today), and does it also do first-level KYC checks? | Roles | CS reviews | Phase 8 |
| OQ-O2 | Which go-live tasks **block** activation (branding, DLT, SMS templates, Play Store, custom domain)? Is activation manual or automatic once the gate passes? | Activation gate | Only platform tasks block; manual activation | Phase 10 |
| OQ-O3 | Maker-checker levels: reviewer → approver; is activation a third person? | Separation of duties | Two levels; activation by OPS | Phase 8 |
| OQ-O4 | What happens to applications in flight in today's process (Connect + Go-Live) at cut-over? | Migration | Finish in the old flow | Phase 12 |
| OQ-O5 | Review SLAs and escalation paths | Timers, alerts | — | Phase 8 |
| OQ-O6 | Who handles DLT registration with telecom operators: the partner or Eko on the partner's behalf? | Go-live task ownership | Partner, assisted by Eko | Phase 10 |
| OQ-O7 | Support model during onboarding (chat / phone / email), and does support need read-only access to applications? | RBAC | Read-only role | Phase 8 |

# ELOKA backend: implementation plan (approved 2026-10-15)

## Context

`eloka/` has a working Next.js frontend (`client/`) that today gets all its data from Eko's EPS, plus a Spring Boot skeleton (`server/`) with no features. The user wants **our own production-ready backend** that powers the existing screens (admin dashboard, network, agent home, settings, onboarding, as in the two screenshots) plus the **Local Admin onboarding** from the blueprint (`docs/onboarding/`, PDF on the Desktop). Requirements: easy local testing with fixed credentials, no surprises on deploy, industry standards, SDLC, OOP, and log files.

Decisions already made by the user:

| Topic | Decision |
|---|---|
| Scope | UI screens + agent onboarding + Local Admin onboarding. Money transactions (EkoConnect widget: DMT, AePS, BBPS, invoices, e-value) are out |
| System of record | **Our server, standalone.** In production the widget is switched off (`NEXT_PUBLIC_DISABLE_TRANSACTIONS=true`) until EPS agrees a token exchange |
| API style | Server **speaks the EPS wire format** so the frontend works unchanged (only env URLs change). New Local Admin + staff screens use clean REST `/v1` |
| OTP SMS | Pluggable port. Local/staging: log-only. Real provider added later by config |
| Local creds | Seeded mobiles + OTP `123456`; impossible to enable in production |
| Deploy target | Undecided → Docker images + env config that run anywhere, plus a full-stack compose and a runbook |
| First release | Screens in the screenshots + both onboardings (DigiKhata, KYC products, BBPS, payments, bulk payment and Ask AI come later) |

Stack (team decision, 14 Oct; it supersedes the PDF's NestJS/Postgres table): **Java 25, Spring Boot 4.1, Spring Modulith 2.1, MySQL 8.4, Flyway, Gradle**.

## Architecture

```
Browser ──► client (Next.js) ──/api/transaction proxy──┐
   └──────── direct calls (/transactions/*, /authentication/*) ──► server :8090 local / :8080 prod
                                                         │
server ─ gateway (EPS wire format) ─► domain modules ─► MySQL
       └ /v1 REST (Local Admin onboarding, staff console)
```

**Module layout** (`server/src/main/java/in/eko/eloka/`; every module = public API interface + `internal/{web,app,domain,infra}`):

| Module | Owns |
|---|---|
| `gateway` (new, top layer, nothing depends on it) | EPS envelope parsing (JSON / form / multipart `form-data`/`formdata` / tf headers + tf URI query), `InteractionHandler` + `TfRouteHandler` strategy registries (fail at startup on duplicates or a missing `AccessPolicy`), `EpsDispatcher`, `EpsEnvelope` builder, file-download wrapper `{file:{blob,name,content-type}}`, idempotency by `client_ref_id` |
| `identity` | OTP challenges, sessions (opaque tokens), transaction PIN, staff users |
| `tenant` | orgs, domains, metadata/theme (677/774), business settings (737/726), `org_token` |
| `network` (new) | users, roles, hierarchy (distributor subtree), profile/shop/address/GSTIN, demo users |
| `wallet` (new) | wallet accounts + append-only ledger (balance read model) |
| `history` (new) | transaction records, statements, earnings, dashboard aggregates |
| `pricing` (new) | products/menu (`/transactions/wlc`), pricing tree 837, rules 1046/838 |
| `support` (new) | issue types, tickets, comments, attachments |
| `notification` | in-app notifications; `SmsSender`/`EmailSender` ports |
| `documents` | file storage port (filesystem volume first, S3 later), type sniffing |
| `agentonboarding` (new) | agent KYC plan/steps, KYC record (never the Aadhaar number), bank, agreement |
| `onboarding`, `verification`, `agreement`, `whitelabel`, `review`, `provisioning`, `lead` | Local Admin blueprint |
| `audit` | hash-chained `audit_event`, written in the caller's transaction |
| `integration` | fake/real adapters for PAN, DigiLocker, e-sign, bank, SMS |
| `devdata` (`@Profile("local")` only) | seeder + `POST /v1/dev/reset` |
| `shared` (exists, open) | `DomainException` family, `Caller`/`Role`/`CallerContext`, crypto (Tink AEAD + HMAC blind index + masking), `ProductionSafetyGuard`, request-id filter, clock |

**Key rules (OOP and safety):**
- Handlers never read identity (`user_code`, `initiator_id`, `org_id`, `bc`) from the body. The frontend hard-codes `99029899` / `1234567891`; the caller always comes from the token.
- Every org-scoped query is filtered by the caller's org (Hibernate `@TenantId` + default-deny resolver; fallback is explicit repository filters if the P1 spike fails). Distributors see only their subtree. Out-of-scope ids return "not found".
- Business failures = `DomainException` → HTTP 200 + EPS fail envelope (with the right `response_type_id`). Auth failure = 401 + JSON. Every response is JSON (the proxy calls `res.json()`).
- Entities guard their own state changes (no public setters); constructor injection; records for DTOs; `ModularityTests` keeps module boundaries.

**Auth:** opaque random tokens (`elk_at_…`, `elk_rt_…`), only SHA-256 stored in `auth_session`, so revocation is instant (logout, deactivate, role change).
- 518 returns the token as `data.otp_verification_token`, plus `refresh_token` and `token_expiration` (ignored by today's client).
- Until the client stores the refresh token (FE-3): 2 h sliding idle, 12 h absolute. After: 30 min access + refresh rotation with reuse detection (**on since P6**, `ELOKA_ACCESS_TOKEN_TTL`).
- `/authentication/token` answers 200 + a fail envelope on a bad refresh token. Today's client sends `"undefined"` after a reload, and a 401 there would log everyone out.
- Local Admin applicants use the same bearer tokens (`kind=LA_APPLICANT`, only valid on `/v1/onboarding/**`) with step-up OTP for bank, e-sign and withdraw. ADR-0005 records this deviation from the blueprint's cookie BFF.
- Eko staff use Google sign-in → opaque staff session + permissions with maker-checker. Locally, staff log in with OTP `123456`.

## Local mode (fixed creds)

- `devdata/LocalSeedRunner` (local profile only, idempotent) loads `server/src/main/resources/devdata/local-seed.json`, ported from `client/tools/mock-eps/data/users.js` + `catalog.js`. Seed orgs: `demo` (tenant), `partners` (Local Admin applications), `staff`.

  | Mobile | Who |
  |---|---|
  | 9000000001 | retailer |
  | 9000000002 | distributor |
  | 9000000003 | org admin |
  | 9000000004 | unseeded → new user |
  | 9000000005 | half-onboarded retailer |
  | 9000000006 | staff reviewer |
  | 9000000007 | staff approver |

  All use OTP `123456`.
- `OtpCodeGenerator` has two implementations: `SecureRandomOtpGenerator` (default) and `FixedOtpGenerator` (`@Profile({"local","test"})`). `LogOnlySmsSender` logs the masked mobile; codes are logged only with `reveal-codes`, allowed in local/staging only.
- **`ProductionSafetyGuard`:** when `eloka.environment=production` (the default in `application-prod.yaml`), startup fails if any `UnsafeInProduction` bean or flag exists (fixed OTP, reveal codes, seeder, dev reset, body logging, OTP-in-response). A test proves this.
- Reference data that is real for all environments (states, banks, shop types, issue types, products, onboarding plans) goes in Flyway migrations, not the seeder.

## Logging

- `logback-spring.xml` + Spring Boot's **built-in structured logging** (no extra library):
  - `logs/eloka-app.log`: daily + 100 MB rolling, gzip, 30 days, 5 GB cap
  - `logs/eloka-access.log`: one line per request: request id, method, path without query, interaction/tf route, status, latency, org, user code; 90 days
  - `logs/eloka-audit.log`: mirrors `audit_event`; 365 days
- Local: coloured console + text files in `server/logs/` (gitignored). Prod: JSON to stdout + the files on a mounted volume (`ELOKA_LOG_DIR`).
- `RequestContextFilter`: `X-Request-Id` in/out, with MDC keys `request_id`, `org_id`, `user_code`, `interaction`.
- PII never logged: bodies are not logged; a `PiiMasker` safety net covers mobile, PAN, Aadhaar, account numbers, OTPs and tokens, with tests. nginx logs `$uri`, not `$request_uri` (the tf proxy puts body fields in the query string).

## Database (Flyway, MySQL 8.4)

Conventions: UUIDv7 `BINARY(16)` keys; numeric ids where EPS shows them (org id, user_code, tid); `DATETIME(6)` UTC; `DECIMAL(18,2)` money; PII as `_enc` + `_hash` + `_masked`; one logical change per file.

| Migrations | Tables |
|---|---|
| V1 (exists) | event_publication |
| V2 tenant | org, org_domain, org_metadata, org_setting |
| V3 identity | otp_challenge, auth_session, transaction_pin, staff_user, staff_role_grant |
| V4 network | app_user, user_hierarchy, shop_profile, user_address, user_gstin, id_sequence |
| V5 wallet | wallet_account, ledger_entry |
| V6 history | txn_record |
| V7 pricing | product, org_product, pricing_rule |
| V8 support | issue_type, support_ticket, comment, attachment |
| V9 notification | notification, notification_receipt, outbound_message |
| V10 documents | stored_document |
| V11 agent onboarding | onboarding_plan(+step), agent_onboarding(+step), agent_kyc_record, agent_bank_account, agent_agreement |
| V12–V13 | audit_event, idempotency_key |
| V14–V16 (Release 2) | Local Admin tables from `docs/onboarding/DATABASE_DESIGN.md` |
| V17 | reference data |

Provisioning creates an org + Local Admin in our own DB, in **one transaction** (idempotent via `UNIQUE(org.application_id)`).

## Phases (SDLC: each phase = design note → build → tests → review → demo)

> Status (2026-10-16): **P0–P8 done** (P6 open items: staging host, tag). Next: P9. Per-call progress: [`eps-compat/CONTRACT.md`](./eps-compat/CONTRACT.md).

**Release 1: our server replaces EPS for the existing screens**

| Phase | Deliverables | Done when |
|---|---|---|
| P0 Contract | ADR-0004 (standalone + EPS wire format), ADR-0005 (opaque tokens), ADR-0006 (logging/secrets). `docs/eps-compat/CONTRACT.md` (one row per interaction). **Extend `client/tools/mock-eps/test` to every Release 1 interaction**; it becomes the executable spec. Update CLAUDE.md / ARCHITECTURE_OVERVIEW (they still say EPS is the system of record) | Contract reviewed; `npm run test:mock` green |
| P1 Foundation + login | Logging, request id, crypto, audit, safety guard; gateway core; identity (515/518/151, revoke, token, refresh-profile, getip); tenant (677, 774); seeder; server Dockerfile (layered); `deploy/docker-compose.yml` full stack | Logins 9000000001/2/3 reach their homes via `npm run dev` against the server; contract "login" block green against Java |
| P2 Homes + dashboards | 9, `/transactions/wlc`, 10010/10012/10023, 623, 154, pricing_commission, 818, 1044, 682, 817/683/727 | Both home pages and both dashboard tabs render; no "unsupported" in logs |
| P3 Network, history, profile | `/network/agents` (paging, filters), agent-list, agent profile + documents, updateStatus 1831, changeRole 1842/1967/1872, personalInfo/address update, 792, demo delete, statements, 154 network/self + 183 export, `/reports/agent-subnetwork`, 401, 373 | Admin + distributor network flows end to end; cross-org and cross-subtree access denied |
| P4 Settings, pricing, onboard agents, support | 737 ops, 726, agreement-signing v2 + email OTP 553/554/555, 353; 837/1046/838/756; multiple_onboarding (+demo), bulk_onboarding (CSV/XLSX); 692, 10016–10020, 10022, 10000 | All those screens work; replayed `client_ref_id` does not double-apply |
| P5 Agent onboarding | 521, 523 (doc types 1/2/3/7), 298, 522, 552 + DigiLocker fake page, 454, 155, 170, 10005, 5, 287/293, 519, 387, 194, 197, refresh-profile `{csp_id}` | Mock's self-onboarding + resume tests pass against Java; DB scan proves no Aadhaar number stored. **Done 2026-10-16**: contract journey green on mock and server; the schema has no Aadhaar-number column (only front/back image ids, V18) |
| P6 Hardening + release | FE-1..FE-5; refresh rotation on; k6 load test on login/home; OWASP dependency check, Trivy, gitleaks; backup/restore drill; `docs/deploy/RUNBOOK.md` | Staging sign-off, tag `v1.0.0`. **Done 2026-10-16:** FE-1..FE-5, 30-min access tokens + rotation, k6 (`server/load/home.js`, ~1,080 req/s, p95 59 ms, 0 errors after fixing a session-touch race it found), gitleaks + Trivy + dependency review + Dependabot (instead of OWASP dependency-check: Trivy scans the jars and node modules inside the shipped images), client image + Caddy edge (ADR-0007), first-tenant bootstrap, backup/restore drill passed. Then (same day): client lint and all 246 Jest suites green, Next.js 15.5.28. **Open:** staging on a real host, tag |

**Release 2: Local Admin onboarding (blueprint)**

| Phase | Deliverables | Done when |
|---|---|---|
| P7 Applicant API | A1–A30 with fakes for PAN, DigiLocker, bank, e-sign; state machine + ETag/If-Match + Idempotency-Key | Applicant goes from OTP to SUBMITTED. **Done 2026-10-16** ([design note](./onboarding/P7_APPLICANT_API.md), 11 integration scenarios) |
| P8 Staff review API | S1–S13, S21, maker-checker | Reviewer approves/rejects/corrects; same person cannot recommend and approve. **Done 2026-10-16** ([design note](./onboarding/P8_STAFF_REVIEW.md), 8 integration scenarios) |
| P9 Provisioning + go-live | Listener on `ApplicationApproved` → org + domain + metadata + Local Admin + default products; S14–S18 activation | Approved applicant logs in at `{label}.{root}` as Local Admin |
| FE-R2 (parallel to P7–P9) | `client/src/features/la-onboarding/` (routes `src/pages/partner/onboarding/*` on `partners.<root>`), reusing `src/features/onboarding` step components, `src/components/ui/Dropzone` and the `VerifyOtp` UI; `client/src/features/staff-console/` (`src/pages/staff/*` on `staff.<root>`); typed client generated from springdoc OpenAPI | Full journey clickable locally with fixed creds |

## Frontend changes (kept small)

- Env only:
  - `NEXT_PUBLIC_API_BASE_URL` = server origin (no path)
  - `ORG_META_SERVICE` = server URL, plus the shared secret
  - `WLC_SUBDOMAIN_ROOT`
  - Production: `NEXT_PUBLIC_DISABLE_TRANSACTIONS=true` and `NEXT_PUBLIC_ENABLE_SECURITY_HEADERS=true`
  - New `npm run dev:full` (compose: MySQL + server + client). Local URL `http://demo.eloka.localhost:3002`
- Small fixes:

  | Id | Fix | File |
  |---|---|---|
  | FE-1 | Proxies forward the 401 status, `X-Forwarded-For` and `X-Request-Id` | `src/pages/api/transaction.js`, `transactionjson.js`, `upload.js` |
  | FE-2 | Logout sends Authorization | `src/lib/loginHelper.js` |
  | FE-3 | Keep `refresh_token` / `token_expiration` from the 518 response | `src/hooks/useLogin.js` |
  | FE-4 | Auth header on `/reports/agent-subnetwork` | `src/screens/Admin/Network/Network.jsx` |
  | FE-5 | API domain in CSP `img-src` | `next.config.js` |

- `client/Dockerfile` gets build `ARG`s for `NEXT_PUBLIC_*` (baked in at build time → one client image per environment).
- `client/tools/mock-eps`: kept as the fast UI-only tool until P5. After Release 1 the server is the default and the mock code is retired; the contract tests stay and run against the server (`EPS_CONTRACT_BASE_URL`).

## Deployment (runs anywhere)

- **Server image:** layered jar on `eclipse-temurin:25-jre`, non-root, `curl` for health checks, `/app/logs` and `/app/data/files` volumes.
- **`deploy/docker-compose.prod.yml`:**

  | Service | Details |
  |---|---|
  | `mysql` | 8.4, volume, healthcheck |
  | `migrate` | one-shot Flyway with the DDL user |
  | `server` | DML-only user, `ddl-auto=validate`, management port 8081 for health |
  | `client` | — |
  | `edge` | nginx: TLS, wildcard certificate, `limit_req`, `$uri` logs, `/actuator` not exposed |
  | `backup` | nightly encrypted `mysqldump` + file backup, 7/4/12 retention, monthly restore drill |

- **Secrets:** env file or Docker secrets (`configtree:/run/secrets`), never in git.
- **Env catalogue** (in the runbook): DB, keys (crypto, blind index, OTP pepper, org-token, org-meta secret), CORS patterns, root domain, session TTLs, SMS/email provider, storage, log dir, trusted proxies, staff Google client.
- **Migrations:** expand/contract only, so rollback = previous image.

## Size and working agreements

- **Size:** about 90 EPS interactions/routes in Release 1 (one small handler class each, grouped per screen) plus about 45 `/v1` endpoints in Release 2. Each phase is one or more PRs into `dev` and is demoed with fixed creds before the next starts.
- **Git:** commit on the checked-out branch (no new branch without asking), no AI attribution trailers, never push. Each phase ends with lint/tests green and docs (CLAUDE.md, CONTRACT.md, ADRs) updated in the same change.

## Critical files

- **Modify:** `server/.../shared/security/SecurityConfig.java` (ordered chains), `shared/web/ApiExceptionHandler.java` (scope to `/v1`, add `invalid_params`/`retry_after`), `server/build.gradle.kts`, `application*.yaml`, `server/Dockerfile`, `CLAUDE.md`, `docs/ARCHITECTURE_OVERVIEW.md`, `client/Dockerfile`, FE-1..FE-5 files.
- **Reuse:** `DomainException`, `ClockConfig`, `ModularityTests`, `compose.yaml`; `client/tools/mock-eps/lib/MockEpsServer.js` (routing order/envelope reference), `client/tools/mock-eps/data/*` (seed source), `client/tools/mock-eps/test/MockEpsServer.test.js` (executable contract); `src/features/onboarding` step components.
- **New:** `gateway/`, `network/`, `wallet/`, `history/`, `pricing/`, `support/`, `agentonboarding/`, `devdata/`, `logback-spring.xml`, `V2–V17` migrations, `deploy/`, `docs/eps-compat/CONTRACT.md`, `docs/deploy/RUNBOOK.md`, ADRs 0004–0006.

## Verification (every phase)

1. `cd server && ./gradlew test`:
   - unit tests (parser variants, OTP expiry/attempts/limits, sessions, masking, state machines)
   - `@WebMvcTest` gateway (envelope, 200 vs 401, JSON errors, CORS, multipart)
   - Testcontainers MySQL (repositories, scoping, idempotency)
   - **table-driven access tests over every registered handler** (org A token cannot reach org B; retailer cannot call admin interactions)
   - `ProductionSafetyGuardTest`
   - `ModularityTests`
2. Contract suite against Java: `docker compose -f deploy/docker-compose.ci.yml up -d` then `cd client && EPS_CONTRACT_BASE_URL=http://localhost:8090 npm run test:mock`.
3. Browser E2E with fixed creds (the headless-Chrome login script used earlier, extended per phase): each seeded user lands on the right page with data and **no `Unsupported request` in `logs/eloka-access.log`**.
4. Prod-like run: `docker compose -f deploy/docker-compose.prod.yml up` with `ELOKA_ENVIRONMENT=production`. Check:
   - fixed OTP is rejected
   - health checks are green
   - log files are written and rotated
   - restore from backup works
5. CI (`.github/workflows/ci.yml`): build + tests + contract job + CodeQL + gitleaks on every PR to `dev`.

## Risks / open questions (tracked in `docs/onboarding/OPEN_QUESTIONS.md`)

1. With transactions off and no EPS, real tenants start with **zero balances and empty history**. They need an import or admin-credit source later.
2. EPS response shapes the mock does not cover (682 contents, 837 tree, 1046, 692, file exports, 737) are the main schedule risk. P0 should capture UAT samples if Eko can provide them; otherwise we define shapes from the frontend code.
3. KYC in Release 1 only **collects** documents (PAN, DigiLocker, bank and e-sign are fakes). Real providers need contracts. Aadhaar images need a compliance decision (masking/vault).
4. Unverified Boot 4 compatibility: Tink, springdoc 3, fastexcel/OpenPDF, ArchUnit, and Hibernate `@TenantId` on MySQL. A spike in P1 has fallbacks ready.
5. Staff identity provider (Google Workspace assumed) and MFA enforcement.
6. Hosting target, domain and TLS (wildcard certificate vs Caddy on-demand TLS for custom domains).

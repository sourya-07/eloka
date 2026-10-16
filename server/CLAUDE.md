# CLAUDE.md — server/

The ELOKA backend: **Java 25, Spring Boot 4.1, Spring Modulith 2.1, MySQL 8.4, Flyway, Gradle (Kotlin DSL)**.
Read the root [`CLAUDE.md`](../CLAUDE.md) first. Decisions: ADR-0004 (system of record, EPS wire format), ADR-0005 (opaque tokens), ADR-0006 (logging, secrets). Plan and phases: [`docs/BACKEND_PLAN.md`](../docs/BACKEND_PLAN.md).

> Status: **P1–P6 done** (Release 1: EPS-compatible screens, hardening). **P7 done**: Local Admin applicant API (`/v1/onboarding`, A1–A30, [design note](../docs/onboarding/P7_APPLICANT_API.md)). **P8 done**: staff review API (`/v1/admin`, S1–S13, S21, [design note](../docs/onboarding/P8_STAFF_REVIEW.md)). Next: P9 provisioning. Per-call progress: [`docs/eps-compat/CONTRACT.md`](../docs/eps-compat/CONTRACT.md).

## Commands

```bash
./gradlew bootRun          # :8090, profile `local`: starts MySQL (compose.yaml), migrates, seeds dummy users. Docker must run.
./gradlew test             # all tests (Testcontainers MySQL needs Docker)
./gradlew test --tests 'in.eko.eloka.ModularityTests'   # module-boundary check only
./gradlew bootJar          # build/libs/eloka-server-<version>.jar
docker build -t eloka-server:<version> .                # production image (layered, non-root, prod profile)
make -C .. load-test       # k6: home-page calls against :8090 (load/home.js)
```

Gradle needs a JDK 17+ to start (the default `java` on some Macs is 1.8): `export JAVA_HOME=/opt/homebrew/opt/openjdk` or a Temurin 25. The Java 25 toolchain downloads automatically.

Check it: `curl localhost:8090/v1/system/info`, `curl localhost:8090/actuator/health`. Logs: `logs/eloka-{app,access,audit}.log`.

## Layout

```
src/main/java/in/eko/eloka/
├── shared/        OPEN module: errors (DomainException family), Caller/Role/SecurityConfig, crypto (FieldCipher,
│                  BlindIndex, Tokens, Masking, Mobiles), logging (RequestIdFilter, PiiMasker), ProductionSafetyGuard,
│                  BaseEntity (UUIDv7 id, version, timestamps), clock
├── gateway/       EPS wire format → domain calls. internal/{envelope, dispatch, web, report (XLSX/PDF/CSV), handler/<area>}
├── identity/      login, email, agent-consent, applicant and re-auth OTPs (OtpService), sessions (SessionService =
│                  CallerResolver; kinds USER, PRE_REGISTRATION, LA_APPLICANT, STAFF), re-auth tokens, PinTwin + PIN,
│                  staff accounts + roles (StaffService, StaffRole), staff sign-in (Google / local OTP), IdentityApi
├── agentonboarding/ KYC steps per role (KycStep, plans), progress + encrypted KYC data, funnel stages (AgentOnboardingApi)
├── integration/   ports for outside providers (DigiLockerProvider, EsignProvider, PanVerificationProvider,
│                  BankAccountVerifier, GoogleIdentityVerifier); fakes in local/test, "not configured" elsewhere
├── onboarding/    Local Admin applications (P7): applicant, application + step state machines (StepKey, StepContext,
│                  ApplicationStateMachine), step policy (OnboardingProperties), /v1/onboarding controllers, Idempotency,
│                  ETag/If-Match (WebSupport), application documents, expiry job; staff review (ReviewService,
│                  ReviewQueries, StaffReviewController: claim, corrections, maker-checker approve/reject, PII reveal)
├── verification/  PAN, DigiLocker KYC and bank attempts (VerificationApi), NameMatch
├── whitelabel/    sub-domain availability + atomic reservation, branding drafts (WhiteLabelApi)
├── agreement/     agreement versions rendered to PDF, e-sign sessions; SIGNED only after the provider confirms (AgreementApi)
├── tenant/        orgs, domains, public metadata, org tokens (OrgApi, OrgTokens); /v1/system/tls-allowed for the TLS edge
├── network/       users, shops, hierarchy; scoped queries + commands (UserDirectory, UserCommands, NetworkQueries, NetworkCommands)
├── notification/  SmsSender port + LogOnlySmsSender; in-app inbox (NotificationApi)
├── wallet/        wallet accounts + append-only ledger (WalletApi); opened on UserCreated
├── history/       transaction read model, earnings, dashboard aggregates (HistoryApi)
├── pricing/       product catalog = app menu (CatalogApi); pricing rules and commissions (PricingApi)
├── support/       issue types, tickets, comments, attachments (query center, raise issue)
├── documents/     uploaded files: type-checked, encrypted at rest, DocumentStorage port (filesystem now)
├── reference/     dropdown lists: states, shop types, banks (ReferenceData)
├── audit/         AuditLog: hash-chained audit_event, mirrored to eloka-audit.log
├── devdata/       local profile only: LocalSeedRunner (devdata/local-seed.json)
├── system/        /v1/system/info, migrate profile runner, first-tenant bootstrap (profile `bootstrap`, TenantBootstrap)
└── provisioning/ lead/  (Release 2: P9, empty)
src/main/resources/
├── application.yaml / application-local.yaml / application-prod.yaml
├── logback-spring.xml          three rolling files; text locally, JSON (ECS) in prod
├── db/migration/V<n>__*.sql    Flyway, one logical change per file, never edit one that has run
└── devdata/local-seed.json     dummy orgs/users (local only)
```

## Adding an EPS call (the common task)

1. Find it in `docs/eps-compat/CONTRACT.md` (request fields, response fields the client reads, success id).
2. Domain first: add the use case to the owning module's public interface (e.g. `UserDirectory`) and implement it in `internal/`. No EPS field names in domain code.
3. Gateway: one `@Component class XxxHandler implements EpsHandler` in `gateway/internal/handler/<area>/`:
   - `routes()`: `RouteKey.interaction(9)`, `RouteKey.tf("/ekoicici/v1", HttpMethod.GET, "/network/agents")` or `RouteKey.path(...)`
   - `access()`: `PUBLIC`, `SESSION`, `USER`, `DISTRIBUTOR_OR_ADMIN` or `ORG_ADMIN` (required; startup fails without it)
   - `handle()`: read params with `call.params().string/requireString/integer`, identity **only** from `call.caller()`, build the reply with `EpsEnvelope.ok()...build()` / `EpsEnvelope.list(...)` / `new EpsResponse.File(...)`.
4. Tests: domain unit test, a case in an integration test (`gateway/*IntegrationTest`), and the contract test in `client/mock-eps/test` (phase-gated with `until(n)`).
5. Mark the row ✅ in CONTRACT.md.

## Rules (OOP + safety)

- **Modules:** talk only through another module's top-level package (its API) or events; `internal` is private. `ModularityTests` enforces it.
- **Entities guard their rules:** no public setters; intention-revealing methods (`session.rotate(...)`, `challenge.recordFailure()`); constructors set required state.
- **Identity:** never from request bodies (the client hard-codes `user_code`/`initiator_id`). Public login calls identify the tenant by `org_token`.
- **Org scoping:** every repository lookup takes `orgId` (e.g. `findByIdAndOrgId`). Out-of-scope = "not found".
- **Personal data:** mobile/email/PAN/account stored as `*_enc` (FieldCipher) + `*_hash` (BlindIndex) + masked copy. Never log them; `PiiMasker` is only the safety net.
- **Errors:** throw `DomainException` subclasses (`NotFoundException`, `ConflictException`, `ValidationException`, `ForbiddenException`, `RateLimitedException`, `UnauthenticatedException`). The gateway maps them to HTTP 200 + fail envelope (401 for unauthenticated).
- **Audit:** state changes call `AuditLog.record(...)` inside the same transaction, with ids/codes only. Read one target's trail with `AuditTrail.forTarget(...)`.
- **Staff (`/v1/admin`):** check a permission (`ReviewService.require(caller, StaffPermission.X)`) in every action; permissions are reloaded per request. Maker-checker lives in `ReviewService.approve`. Staff actions take the row lock and the `If-Match` version through `ReviewService.lock(...)` (claim from the queue is the only exception). Refresh a token only for the session kinds the endpoint serves (`IdentityApi.refresh(token, kinds)`).
- **Time:** inject `Clock`.
- **`/v1` REST (Release 2):** problem+json errors (`ApiProblem` for extra members such as `current`); snake_case fields; side-effect POSTs go through `Idempotency.run(...)`; writes to an application go through `ApplicationService.write(...)` (row lock + `If-Match` + editable check). Never mark a verification or signature done from what the browser sends: ask the provider port.
- **Hot rows:** a browser sends several calls at once on one session. Do not update a shared row (session, wallet) through a versioned entity on every read; use a guarded `@Modifying` update (see `AuthSessionRepository.touch`). `server/load/home.js` (k6) catches this.
- **Schema:** Flyway only (`ddl-auto=validate`). `DATETIME(6)` UTC, `BINARY(16)` UUIDv7 ids, `DECIMAL(18,2)` money.
- **Dev-only behaviour** implements `UnsafeInProduction` (beans) or `SafetyCheck` (config). `ProductionSafetyGuard` blocks production start if any is active.

## Profiles

| Profile | Used for | Database | Notes |
|---|---|---|---|
| `local` (default) | laptop | MySQL 8.4 from `compose.yaml` on :3307 | fixed OTP 123456, OTP in response, seed data, dev keys |
| `test` | automated tests | Testcontainers MySQL | fixed OTP, test keys (`src/test/resources/application-test.yaml`) |
| `prod` | staging + production | `ELOKA_DB_*` | everything from env vars; `ELOKA_ENVIRONMENT=production` by default; JSON logs |
| `prod,migrate` | one-shot migration job | same | runs Flyway, validates, exits 0 |
| `prod,bootstrap` | one-shot first tenant | same | creates org + Local Admin from `ELOKA_BOOTSTRAP_*`, exits (idempotent) |
| `prod,staff-bootstrap` | one-shot staff account | same | creates/updates an Eko staff account from `ELOKA_STAFF_*`, exits |

Deployment: `deploy/docker-compose.yml` + `docs/deploy/RUNBOOK.md`.

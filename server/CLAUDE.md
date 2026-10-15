# CLAUDE.md — server/

The ELOKA backend: **Java 25, Spring Boot 4.1, Spring Modulith 2.1, MySQL 8.4, Flyway, Gradle (Kotlin DSL)**.
Read the root [`CLAUDE.md`](../CLAUDE.md) first. Decisions: ADR-0004 (system of record, EPS wire format), ADR-0005 (opaque tokens), ADR-0006 (logging, secrets). Plan and phases: [`docs/BACKEND_PLAN.md`](../docs/BACKEND_PLAN.md).

> Status: **P1–P4 done** (foundation, login, org lookup, home, dashboards, network, history, profile, settings, pricing, onboard agents, support). Next: P5 (agent onboarding). Per-call progress: [`docs/eps-compat/CONTRACT.md`](../docs/eps-compat/CONTRACT.md).

## Commands

```bash
./gradlew bootRun          # :8090, profile `local`: starts MySQL (compose.yaml), migrates, seeds dummy users. Docker must run.
./gradlew test             # all tests (Testcontainers MySQL needs Docker)
./gradlew test --tests 'in.eko.eloka.ModularityTests'   # module-boundary check only
./gradlew bootJar          # build/libs/eloka-server-<version>.jar
docker build -t eloka-server:<version> .                # production image (layered, non-root, prod profile)
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
├── identity/      login OTP (OtpService), sessions (SessionService = CallerResolver), IdentityApi
├── tenant/        orgs, domains, public metadata, org tokens (OrgApi, OrgTokens)
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
├── system/        /v1/system/info, migrate profile runner
└── onboarding/ verification/ documents/ agreement/ whitelabel/ review/ provisioning/ lead/ integration/  (Release 2, empty)
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
- **Audit:** state changes call `AuditLog.record(...)` inside the same transaction, with ids/codes only.
- **Time:** inject `Clock`.
- **Schema:** Flyway only (`ddl-auto=validate`). `DATETIME(6)` UTC, `BINARY(16)` UUIDv7 ids, `DECIMAL(18,2)` money.
- **Dev-only behaviour** implements `UnsafeInProduction` (beans) or `SafetyCheck` (config). `ProductionSafetyGuard` blocks production start if any is active.

## Profiles

| Profile | Used for | Database | Notes |
|---|---|---|---|
| `local` (default) | laptop | MySQL 8.4 from `compose.yaml` on :3307 | fixed OTP 123456, OTP in response, seed data, dev keys |
| `test` | automated tests | Testcontainers MySQL | fixed OTP, test keys (`src/test/resources/application-test.yaml`) |
| `prod` | staging + production | `ELOKA_DB_*` | everything from env vars; `ELOKA_ENVIRONMENT=production` by default; JSON logs |
| `prod,migrate` | one-shot migration job | same | runs Flyway, validates, exits 0 |

Deployment: `deploy/docker-compose.yml` + `docs/deploy/RUNBOOK.md`.

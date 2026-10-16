# CLAUDE.md

Guidance for anyone (people and AI agents) changing this repository. **Read this first**, then the `CLAUDE.md` of the folder you work in:

- [`client/CLAUDE.md`](./client/CLAUDE.md): frontend (Next.js)
- [`server/CLAUDE.md`](./server/CLAUDE.md): backend (Spring Boot)

Keep these files true. When a structure, command or rule changes, update the matching `CLAUDE.md` in the same PR. Write only proven rules, not wishes.

## What this project is

ELOKA is Eko's white-label fintech platform. Tenants (businesses) get a branded portal run by a **Local Admin**; their agents (distributors, retailers) onboard through KYC steps and use the portal. The web app was written against **EPS** (Eko Platform Services). Since 2026-10-15 (ADR-0004) **`server/` is the system of record and speaks the EPS wire format**, so the client works unchanged; money transactions (EkoConnect widget) are switched off in production. Release 2 adds **Local Admin onboarding** with an Eko staff console. Plan: [`docs/BACKEND_PLAN.md`](./docs/BACKEND_PLAN.md).

One-page picture: [`docs/ARCHITECTURE_OVERVIEW.md`](./docs/ARCHITECTURE_OVERVIEW.md). Onboarding design: [`docs/onboarding/`](./docs/onboarding/ARCHITECTURE.md).

## Repository map

```
eloka/
├── client/            Next.js 15 web app, Pages Router (moved here from the repo root on 2026-10-15)
│   └── mock-eps/      dummy EPS server for local logins (dev tool, not shipped)
├── server/            Java 25 + Spring Boot 4.1 + MySQL 8.4 backend (system of record, speaks the EPS wire format)
├── deploy/            production stack: docker-compose (mysql, migrate, server, client, Caddy edge), backup/restore scripts
├── docs/              overview, local dev, workflow (SDLC), ADRs, onboarding blueprint, deploy runbook
├── .github/           CI (path-filtered per folder), CodeQL, PR template
└── Makefile           `make help`
```

`client/` and `server/` are independent: own dependencies, build, tests and deploy. They talk only over HTTP. Never import code across them.

## Commands

| Task | Command |
|---|---|
| Website with dummy logins (mock) | `cd client && npm run dev:mock` → <http://localhost:3002>, OTP `123456` |
| Website + real backend | `cd server && ./gradlew bootRun`, then `cd client && npm run dev:server` |
| Website against real EPS | `cd client && npm run dev` (settings in `client/.env.local`) |
| Client checks | `cd client && npm run lint:error && npm run test:mock && npm test` |
| Client CI job locally (Node 20, like GitHub) | `make ci-client` (run before pushing client changes) |
| Backend | `cd server && ./gradlew bootRun` (Docker must be running) |
| Backend checks | `cd server && ./gradlew test` |
| Load test (server running) | `make load-test` |
| Production stack | `docs/deploy/RUNBOOK.md` (`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait`) |

Dummy users (all OTP `123456`): `9000000001` retailer, `9000000002` distributor, `9000000003` org admin, `9000000004` new user (self-onboarding), `9000000005` half-onboarded retailer. Details: [`docs/LOCAL_DEVELOPMENT.md`](./docs/LOCAL_DEVELOPMENT.md).

## How we work (SDLC)

Full process: [`docs/DEVELOPMENT_WORKFLOW.md`](./docs/DEVELOPMENT_WORKFLOW.md). The short version:

1. **Plan**: an issue with acceptance criteria. Unknowns go to `docs/onboarding/OPEN_QUESTIONS.md`, never into silent assumptions.
2. **Design**: hard-to-reverse choices get an ADR in `docs/adr/` (copy `0000-template.md`). APIs are agreed before they are built.
3. **Build**: branch from `dev` (`feat/…`, `fix/…`, `chore/…`). Conventional Commits (`feat(server): …`), enforced by commitlint.
4. **Test**: tests for every piece of logic touched; lint + all suites green locally.
5. **Review**: PR into `dev`, one approval (two for auth, money, KYC data, migrations), CI green.
6. **Release**: `dev` → staging, `main` → production with a version bump and a tag.
7. **Operate**: health checks, structured logs without PII, post-mortems feed back into step 1.

## Engineering rules (both sides)

- **Read before you write.** Find the existing pattern and follow it. Match the surrounding code's style and comment density.
- **Small, focused changes.** One concern per PR. Do not refactor unrelated code in the same change.
- **Configuration, not conditionals.** Environment differences live in config (`.env.*` for the client, Spring profiles for the server), never in `if (env === …)` scattered through code. Client feature switches use `constants/featureFlags.ts`.
- **Same build everywhere.** local → staging → production differ only in configuration.
- **Secrets never in git.** Client secrets go in `client/.env.local` (gitignored); server secrets come from environment variables / a secrets manager. `.env.development` holds only safe defaults.
- **PII care.** Never log OTPs, tokens, full PAN / Aadhaar / bank numbers. Mask in logs and UI by default.
- **Every external system behind an interface** (server: port + adapter; client: the `/api/transaction` proxy / `fetcher`), with a local fake so the app runs without the real thing.
- **Tests are part of the change**, not a follow-up.
- **No dead code or debug leftovers** (`console.log`, commented-out blocks) in merged code.

### Client: component-based

Details in [`client/CLAUDE.md`](./client/CLAUDE.md). In short: reusable UI in `components/` (one folder per component with `index`, story and test), route screens in `page-components/`, self-contained features in `features/<name>/` (own components, hooks, context, constants), global state in `contexts/`, logic in hooks, functional components only, use the project's wrapper components over raw Chakra.

### Server: object-oriented, modular

Details in [`server/CLAUDE.md`](./server/CLAUDE.md). In short: one Spring Modulith module per business capability; inside it controller → service → domain → repository/adapter; entities protect their own rules (no public setters, intention-revealing methods); depend on interfaces; constructor injection; records for DTOs and events; `DomainException` subclasses for business errors; Flyway for every schema change; default-deny security.

## Status (keep current)

- 2026-10-15: repo split into `client/` + `server/`; local dummy logins via `client/mock-eps`.
- 2026-10-15: backend plan approved (`docs/BACKEND_PLAN.md`). **P0 + P1 done**: ADRs 0004–0006, `docs/eps-compat/CONTRACT.md`, gateway, login (515/518/151, tokens, logout), org lookup/theme (677/774), audit, logging, production safety guard, seeded local data, Docker image + `deploy/` stack. **P2 done** (2026-10-15): menu, wallet, notifications, earnings, recent transactions, dashboards 818/1044/682/817, wallet/history/notification/pricing-catalog modules, richer seed. **P3 done**: My Network (list, filters, agent profile, status, role change, transfer, edits, demo removal), history and network transactions with XLSX/PDF/CSV export, network statements, own profile edits, reference lookups. Client fixes FE-3 (keep refresh/lite tokens), FE-4 (token on network export), no `"undefined"` tokens in session storage. **P4 done**: business settings (737/726, agreement signature, email OTP 553–555, pincode 353), pricing config and agent commissions (837/1046/838/756), onboard agents by form, demo account and spreadsheet, query center and raise issue (692, 10016–10020, 10022, 10000), encrypted document storage. **P5 done** (2026-10-16): agent self and assisted onboarding (role selection, Aadhaar/PAN/selfie/business/bank/secret PIN/agreement for retailers, DigiLocker plan for distributors, consent OTP for assisted onboarding, admin onboarding funnel 683/727); DigiLocker and e-sign run on local fakes. **P6 in progress** (2026-10-16): client fixes FE-1/2/5 (proxies pass 401, client IP and request id; logout sends the token; API host in CSP), 30-min access tokens with refresh rotation, lock-free session touch (found by the k6 load test), client image + Caddy TLS edge with on-demand certificates, first-tenant bootstrap command, backup/restore scripts (drill passed), security CI (gitleaks, Trivy, dependency review, Dependabot), Tomcat/Jackson patches. Then: client lint clean, all 246 Jest suites green (36 stale suites updated, 6 small bugs fixed), Next.js 15.5.28 (security advisories). Open before `v1.0.0`: staging sign-off on a real host, tag.
- Local Admin onboarding blueprint (`docs/onboarding/`) is the design input for Release 2 (P7–P9).

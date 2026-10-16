# ELOKA

Eko's white-label fintech platform: branded portals for businesses (tenants), run by a Local Admin, used by distributors and retailers for payments and banking services.

| Folder | What | Stack |
|---|---|---|
| [`client/`](./client) | Web app (agents, admins, onboarding) | Next.js 15 (Pages Router), React 18, Chakra UI, TypeScript |
| [`server/`](./server) | Backend: system of record, speaks the EPS wire format | Java 25, Spring Boot 4, Spring Modulith, MySQL 8.4 |
| [`deploy/`](./deploy) | Production stack: Docker Compose, Caddy TLS edge, backup/restore | Docker |
| [`docs/`](./docs) | Architecture, decisions, workflow | Markdown + Mermaid |

## Quick start (local, dummy data)

```bash
cd client && npm install && npm run dev:mock
```

Open <http://localhost:3002>, log in with **`9000000001`** (agent), **`9000000003`** (admin) or **`9000000004`** (new user → onboarding). **OTP: `123456`.** Full list: [docs/LOCAL_DEVELOPMENT.md](./docs/LOCAL_DEVELOPMENT.md).

Backend: `cd server && ./gradlew bootRun` (Docker running), then `curl localhost:8090/v1/system/info`.

`make help` shows all shortcuts.

## Documentation

- [Architecture overview](./docs/ARCHITECTURE_OVERVIEW.md): the one-page picture
- [Local development](./docs/LOCAL_DEVELOPMENT.md): running things, dummy logins
- [Development workflow](./docs/DEVELOPMENT_WORKFLOW.md): branches, commits, reviews, releases
- [Deployment runbook](./docs/deploy/RUNBOOK.md): first deployment, first tenant, backups, rollback
- [Decisions (ADRs)](./docs/adr)
- [Local Admin onboarding blueprint](./docs/onboarding/ARCHITECTURE.md)
- Rules for contributors and AI agents: [CLAUDE.md](./CLAUDE.md)

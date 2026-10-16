 # ELOKA: architecture overview (rough)

> Status: updated 16 October 2026 (Release 1 phases P0–P5 built)
> The detailed design of the first backend feature (Local Admin onboarding) is in [`onboarding/ARCHITECTURE.md`](./onboarding/ARCHITECTURE.md). This page is the one-screen picture of the whole project.

## 1. What we are building

ELOKA is Eko's white-label fintech platform. A business (a **tenant**) gets its own branded portal, run by a **Local Admin**. Inside it, distributors and retailers (**agents**) do money transfer, AePS, bill payment and other services, and new agents onboard themselves through KYC steps.

| Persona | Uses | Today served by |
|---|---|---|
| Applicant (wants to become a tenant) | Local Admin onboarding journey | **new** `server/` + onboarding UI in `client/` |
| Eko staff | Review, approve, provision tenants | **new** `server/` + staff screens |
| Local Admin | Configure network, pricing, branding, onboard agents | `client/` → `server/` (EPS wire format) |
| Agent (retailer / distributor) | Wallet, history, own onboarding; transactions via the EkoConnect widget (off in production) | `client/` → `server/`; widget → EPS |

## 2. System context

```mermaid
flowchart LR
    subgraph Users
        AG[Agents]
        LA[Local Admins]
        AP[Applicants]
        ST[Eko staff]
    end

    subgraph Repo["This repository"]
        C["client/<br/>Next.js 14 web app<br/>(multi-tenant, by sub-domain)"]
        S["server/<br/>Java 25 + Spring Boot 4<br/>ELOKA backend (modular monolith)"]
        DB[(MySQL 8.4)]
    end

    subgraph Eko["Existing Eko platform (not in this repo)"]
        EPS[EPS API<br/>auth, users, wallet, transactions]
        META[Org-metadata service]
        W[EkoConnect widget<br/>transaction screens]
    end

    EXT[External providers<br/>PAN, DigiLocker, e-Sign,<br/>bank, SMS, email]

    EDGE["Caddy edge (TLS)<br/>api.&lt;root&gt; → server, other hosts → client"]
    AG & LA --> EDGE
    AP & ST --> EDGE
    EDGE --> C
    EDGE -->|"direct browser calls"| S
    C -->|"EPS wire format (/api/transaction proxy, internal network)"| S
    C -->|"org lookup 677"| S
    C -.->|"widget only, off in prod"| W
    W -.-> EPS
    C -->|"REST /v1 (Release 2)"| S
    S --> DB
    S -.->|"later: token exchange for the widget"| EPS
    S -->|"ports; fakes until contracts exist"| EXT
```

Key point (ADR-0004, 15 Oct 2026): **`server/` is the system of record and answers the same requests EPS did**, so the client works unchanged. The EkoConnect widget (money transactions) still talks to EPS and is switched off in production until a token exchange with EPS exists. Release 2 adds Local Admin onboarding (application state, staff review, provisioning into our own database).

## 3. Repository layout

```
eloka/
├── CLAUDE.md                 # rules for everyone (people and AI agents)
├── README.md                 # quick start
├── Makefile                  # one entry point for common commands
├── docs/
│   ├── ARCHITECTURE_OVERVIEW.md   # this file
│   ├── LOCAL_DEVELOPMENT.md       # how to run locally + dummy logins
│   ├── DEVELOPMENT_WORKFLOW.md    # SDLC: branches, reviews, releases
│   ├── adr/                       # architecture decision records
│   └── onboarding/                # Local Admin onboarding blueprint (Phase 0)
├── client/                   # frontend (Next.js). Has its own CLAUDE.md
│   └── mock-eps/             # dummy EPS backend for local login/testing
└── server/                   # backend (Spring Boot). Has its own CLAUDE.md
```

## 4. Client (frontend) in one paragraph

Next.js 14 (pages router), React 18, Chakra UI, TypeScript where new. Component-based: `components/` (reusable UI), `page-components/` (one per route), `features/` (self-contained modules such as onboarding, KYC, DigiKhata), `contexts/` (global state), `hooks/`, `tf-components/` (config-driven forms). Every backend call goes through `pages/api/transaction.js`, a server-side proxy to `NEXT_PUBLIC_API_BASE_URL` (our `server/`; EPS only in the legacy `npm run dev` setup). The tenant (branding, features) is resolved from the sub-domain. Details: [`client/CLAUDE.md`](../client/CLAUDE.md).

## 5. Server (backend) in one paragraph

Spring Boot 4 **modular monolith** (Spring Modulith). One deployable, split into modules that own their data and talk through public interfaces or events. Built (Release 1): `gateway` (EPS wire format → domain calls), `identity`, `tenant`, `network`, `agentonboarding`, `wallet`, `history`, `pricing`, `support`, `documents`, `reference`, `notification`, `integration`, `audit`, plus `shared`, `system` and the local-only `devdata`. Planned (Release 2): `lead`, `onboarding`, `verification`, `agreement`, `whitelabel`, `review`, `provisioning`. MySQL 8.4 with Flyway migrations. Layering inside a module: controller → service → domain → repository/adapter. External systems sit behind port interfaces with fake adapters for local runs and tests. Details: [`server/CLAUDE.md`](../server/CLAUDE.md).

```mermaid
flowchart TB
    subgraph module["A module, e.g. onboarding"]
        API[Public API<br/>interface + events]
        WEB[web: controllers, DTO records]
        APP[app: services, transactions]
        DOM[domain: entities with rules]
        INF[infra: repositories, adapters]
        WEB --> APP --> DOM
        APP --> INF
        API -.implemented by.-> APP
    end
    OTHER[Other modules] -->|call| API
    INF --> DB[(MySQL)]
    INF --> PORT[Ports → EPS / providers]
```

## 6. Environments

| | local | staging | production |
|---|---|---|---|
| Client | `npm run dev:server` (real backend) or `npm run dev:mock` (mock only) | `dev` branch build | `main` branch build |
| Server | `./gradlew bootRun`, profile `local`, MySQL in Docker | profile `prod`, staging DB | profile `prod`, production DB |
| Backend for the client | `server/` (or `client/mock-eps`) | `server/` | `server/` |
| Data | dummy users only | test data | real data |
| Secrets | none needed / `client/.env.local` | secrets manager | secrets manager |
| How it runs | `bootRun` + `npm run dev:server` | `deploy/docker-compose.yml` (mysql, migrate, server, client, Caddy edge) | same |

Rule: **the same build runs everywhere; only configuration changes.** Configuration comes from env files (client) and Spring profiles + environment variables (server). Secrets are never committed.

## 7. How a request flows locally

```mermaid
sequenceDiagram
    participant B as Browser :3002
    participant N as Next.js server (client/)
    participant S as server/ :8090 (npm run dev:server)

    B->>N: POST /api/transaction {interaction_type_id: 515, mobile}
    N->>S: POST / (same body)
    S-->>N: {status:0, data:{otp:"123456"}} (local profile only)
    N-->>B: same JSON
    B->>S: POST /transactions/wlc (menu, direct call, Bearer token)
    B-->>S: (Release 2) /v1/onboarding/... REST
```

## 8. Next steps (rough order)

`client/mock-eps` (`npm run dev:mock`) answers the same calls without Java, for UI-only work.

1. **Finish P6:** upgrade Next.js to 15+ (security advisories), clear the client's lint/test debt so CI is green, run the stack on a real staging host, tag `v1.0.0` ([`BACKEND_PLAN.md`](./BACKEND_PLAN.md), [`deploy/RUNBOOK.md`](./deploy/RUNBOOK.md)).
2. **Real providers:** SMS, email, DigiLocker, e-sign (production cannot finish agent onboarding without them, see [`deploy/RUNBOOK.md`](./deploy/RUNBOOK.md)).
3. **Release 2 (P7–P9):** Local Admin onboarding, staff review, provisioning and go-live. It replaces today's Eko Connect + Go-Live tool + email/CRM process ([`onboarding/ELOKA_ONBOARDING_JOURNEY.md`](./onboarding/ELOKA_ONBOARDING_JOURNEY.md) §2). Blocking questions: [`onboarding/OPEN_QUESTIONS.md`](./onboarding/OPEN_QUESTIONS.md).
4. **CD:** a pipeline that builds and publishes images once a deploy target is chosen (CI already builds and scans both images).

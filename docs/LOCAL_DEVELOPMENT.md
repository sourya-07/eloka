# Local development

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Node.js | 20+ (`client/.nvmrc`) | client |
| JDK | 17+ to start Gradle; Java 25 is downloaded automatically | server |
| Docker Desktop | running | server (MySQL), server tests |

## Run the website with dummy data (no real backend needed)

```bash
cd client
npm install          # first time only
npm run dev:mock     # mock EPS on :4010 + website on :3002
```

Open <http://localhost:3002> and log in with one of the numbers below. **The OTP is always `123456`** (it is also shown in a "Demo OTP" toast).

| Mobile | Who | What you can test |
|---|---|---|
| `9000000001` | Ravi Retailer (agent, onboarded) | Agent home, wallet, notifications, history, profile |
| `9000000002` | Divya Distributor (agent, onboarded) | Distributor home + dashboard, network, assisted onboarding |
| `9000000003` | Arjun Admin (org admin) | Admin portal: dashboard, network, onboard agents, pricing pages |
| `9000000004` | New user (not registered) | Full self-onboarding: role selection → KYC steps → home |
| `9000000005` | Meena Midway (retailer, half onboarded) | Resume onboarding at Selfie KYC |
| any other 10 digits | New user | Same as `9000000004` |

Onboarding steps in the mock:

- Retailer: Aadhaar upload → PAN → Selfie KYC → Business details → Bank account → Secret PIN → Sign agreement.
- Distributor: DigiLocker → PAN → Business details → Bank account → Sign agreement.
- On "Tell us who you are?": **I'm a Agent** = retailer, **I'm a Branch Manager** = distributor.
- Selfie KYC asks the browser for location and camera access. Allow both for `localhost`.
- Any file works for uploads. DigiLocker opens a local "Mock DigiLocker" page; close it and continue. The agreement is already signed in the mock, so press **Proceed**. Secret PIN: type any 4 digits twice.

Useful mock endpoints:

```bash
curl localhost:4010/__mock/users            # current state of every dummy user
curl -X POST localhost:4010/__mock/reset    # undo sign-ups and onboarding progress
```

The mock keeps everything in memory, so restarting it also resets the data. If the browser still shows an old session after a reset, log out (or close the tab: sessions live in `sessionStorage`).

**What is not mocked:** the transaction screens themselves (Money Transfer, AePS, bill pay...) run inside the EkoConnect widget, which loads from Eko's servers. Calls the mock does not know return an error that says `has no mock yet`, and the mock terminal prints `(NOT MOCKED)` with the interaction id. Add a handler in `client/mock-eps/handlers/` and a test in `client/mock-eps/test/`.

## Run the website against the real backend (`server/`)

The backend replaces EPS (ADR-0004). It is built phase by phase; calls it does not answer yet return "This feature is not available yet" and show as `(unsupported)` in `server/logs/eloka-access.log`. Progress per call: [`eps-compat/CONTRACT.md`](./eps-compat/CONTRACT.md).

```bash
# terminal 1: backend on :8090 (starts MySQL in Docker, migrates, seeds the dummy users)
cd server && ./gradlew bootRun

# terminal 2: website on :3002 pointed at the backend
cd client && npm run dev:server
```

Same dummy logins and OTP `123456` as the mock. The seed creates the `demo` org (`demo.eloka.localhost`) and the users `9000000001/2/3/5` (plus `9000000011/12` in the network). `9000000004` or any other number is a new user.

Onboarding against the server works like the mock, with these differences: uploads must be real images or PDFs; PAN must look valid (`ABCDE1234F`) and be unused; the secret PIN rejects easy PINs (`1234`, `1111`); DigiLocker opens the server's fake consent page (`/v1/dev/digilocker`); the agreement counts as signed. The server keeps its data, so a finished sign-up stays finished: use a new number or reset the database.

Useful while developing:

```bash
tail -f server/logs/eloka-app.log      # application log (personal data masked)
tail -f server/logs/eloka-access.log   # one line per request: route, outcome, time
tail -f server/logs/eloka-audit.log    # logins, changes (mirror of the audit_event table)
EPS_CONTRACT_BASE_URL=http://localhost:8090 EPS_CONTRACT_PHASE=5 npm run test:mock   # contract tests vs the server
make load-test                         # k6 load test of the home-page calls (Docker)
```

Access tokens last 30 minutes; the website rotates them with the refresh token on its own. To try the full production stack (edge, TLS, images) on your laptop, follow the runbook with `ELOKA_ENVIRONMENT=staging` and `ELOKA_CADDY_GLOBAL=local_certs`: [`deploy/RUNBOOK.md`](./deploy/RUNBOOK.md).

Reset the local database: `cd server && docker compose down -v`, then `./gradlew bootRun` again.

## Run the website against real EPS (UAT)

```bash
cd client
npm run dev          # uses .env.development + your .env.local
```

Put your personal settings and secrets in `client/.env.local` (gitignored), never in `.env.development`. `NEXT_PUBLIC_MOCK_LOGIN=agent|admin` in `.env.local` skips the login screen with a fixed fake session (no backend data).

## Run the server

```bash
cd server
./gradlew bootRun                            # starts MySQL 8.4 in Docker on :3307, app on :8090
curl localhost:8090/v1/system/info
curl localhost:8090/actuator/health
./gradlew test                               # all tests (Docker must be running)
```

The server's local profile uses a fixed OTP (`123456`), returns it in the send-OTP response, and seeds dummy data. None of this can reach production: `ProductionSafetyGuard` refuses to start when `ELOKA_ENVIRONMENT=production` and any of it is active.

If `./gradlew` says it needs Java 17+, point `JAVA_HOME` at a newer JDK, for example `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` after `brew install --cask temurin@25`.

## From the repo root

`make help` lists shortcuts: `make dev-mock`, `make server-run`, `make test`, etc.

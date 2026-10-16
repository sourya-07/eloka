# Documentation index

| Document | Read it when |
|---|---|
| [ARCHITECTURE_OVERVIEW.md](./ARCHITECTURE_OVERVIEW.md) | You want the big picture of client, server and EPS |
| [BACKEND_PLAN.md](./BACKEND_PLAN.md) | You want the approved backend plan and phases (P0–P9) |
| [eps-compat/CONTRACT.md](./eps-compat/CONTRACT.md) | You add or check a call the client makes (status per call) |
| [deploy/RUNBOOK.md](./deploy/RUNBOOK.md) | You deploy, roll back or read production logs |
| [LOCAL_DEVELOPMENT.md](./LOCAL_DEVELOPMENT.md) | You want to run things locally or need the dummy logins |
| [DEVELOPMENT_WORKFLOW.md](./DEVELOPMENT_WORKFLOW.md) | You are about to plan, branch, commit, review or release |
| [commit-message-instructions.md](./commit-message-instructions.md) | You are writing a commit message |
| [adr/](./adr) | You need to know why a big decision was made, or are making one (0007: TLS edge) |
| [onboarding/](./onboarding/ARCHITECTURE.md) | You work on Local Admin onboarding (journey, state machine, DB, API, security, plan, open questions) |
| [onboarding/P7_APPLICANT_API.md](./onboarding/P7_APPLICANT_API.md) | You change the applicant API (`/v1/onboarding`): what was built, deviations, fakes |

Frontend-only docs (animations, feature notes, Docker for the web app) stay in [`client/docs/`](../client/docs).

Note on paths: the onboarding blueprint was written before the split, so code paths in it such as `features/onboarding` or `pages/api/transaction.js` are now under `client/`.

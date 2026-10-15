# ADR-0003: A local mock of EPS for dummy logins

- **Status:** accepted
- **Date:** 2026-10-15
- **Deciders:** project owner

## Context

The client talks to EPS (Eko Platform Services), which is not in this repo and needs network access, test accounts and real OTPs. Developers need to log in and click through the agent, admin and onboarding journeys on a laptop with dummy data. The old `NEXT_PUBLIC_MOCK_LOGIN` switch only fakes the session: there is no login screen and every data call still fails.

## Decision

Add `client/mock-eps/`: a small Node HTTP server (no extra dependencies) that speaks the same envelope and routes as EPS for login, profile, menu, wallet, dashboards, notifications and the agent onboarding steps. It holds dummy users in memory. `npm run dev:mock` starts it with the website pointed at it.

It is a **development tool only**: it is not imported by the app, not part of the production build, and its tokens are fake.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| Standalone mock server (chosen) | Works for both proxied and direct browser calls; zero app changes; easy to read | Must be kept in step with the EPS contract |
| MSW (Mock Service Worker) in the app | Popular; reusable in Jest | Needs hooks in `_app` and the API proxy; harder for server-side calls |
| Mocks inside `server/` | One backend to run | Mixes a fake EPS into the real backend; wrong boundary |

## Consequences

- When the frontend starts using a new EPS interaction, add a handler + test in `client/mock-eps` so local testing keeps working.
- The contract tests in `client/mock-eps/test/` document the EPS shapes the frontend relies on.

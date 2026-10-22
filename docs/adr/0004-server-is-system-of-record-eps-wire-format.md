# ADR-0004: The server is the system of record and speaks the EPS wire format

- **Status:** accepted
- **Date:** 2026-10-15
- **Deciders:** project owner
- **Supersedes:** the "EPS stays the system of record" assumption in `onboarding/ARCHITECTURE.md` and parts of ADR-0003

## Context

The client today depends on EPS (Eko Platform Services) for login, users, network, wallet, history, settings and onboarding. We want our own production-ready backend for those screens, plus Local Admin onboarding, without rewriting the frontend.

## Decision

1. `server/` is the **standalone system of record** for orgs, users, network, wallet balances (read model), transaction history (read model), settings, pricing, support tickets, notifications, agent onboarding and Local Admin onboarding.
2. A `gateway` module accepts the **exact EPS wire format** the client already sends (interaction envelope on `/`, `/transactions/*`, `/authentication/*`, the "tf-req" REST tunnel under `/ekoicici/**`) and translates it into calls on clean domain modules. The client changes only its environment URLs (plus a few one-line security fixes).
3. Money-moving products that run inside the EkoConnect widget (DMT, AePS, BBPS, invoices, e-value) are **out of scope**. In production the client sets `NEXT_PUBLIC_DISABLE_TRANSACTIONS=true` until EPS agrees a token exchange.
4. New features with no EPS precedent (Local Admin onboarding, Eko staff console) use clean REST under `/v1`.

## Consequences

- One contract to honour: `docs/eps-compat/CONTRACT.md`, enforced by the contract tests in `client/tools/mock-eps/test`.
- Handlers never trust identity fields in the body (`user_code`, `initiator_id`, `org_id`, `bc`); identity comes only from the session token.
- Real tenants start with zero balances and empty history until a funding/import source exists.
- `client/tools/mock-eps` becomes redundant after Release 1 and will be retired; its tests stay as the contract.

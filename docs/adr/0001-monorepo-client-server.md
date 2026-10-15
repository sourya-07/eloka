# ADR-0001: One repository with `client/` and `server/`

- **Status:** accepted
- **Date:** 2026-10-15
- **Deciders:** project owner

## Context

ELOKA has an existing Next.js frontend (forked from `wlc-webapp`) and needs a new backend for Local Admin onboarding. Both change together during the first phases: a new onboarding step needs a screen and an API at the same time.

## Decision

Keep both in one Git repository:

- `client/`: the Next.js frontend, moved as-is from the repository root. It keeps its own `package.json`, tooling and `CLAUDE.md`.
- `server/`: the new Java 25 + Spring Boot 4 + MySQL 8.4 backend (decided 14 October 2026, see [`onboarding/ARCHITECTURE.md`](../onboarding/ARCHITECTURE.md) §5).
- `docs/`: system-wide documentation (architecture, onboarding blueprint, ADRs, workflow).

Each folder builds, tests and deploys on its own. They talk only over HTTP (`/v1/...` JSON APIs). No shared code.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| One repo, two folders (chosen) | One PR can change UI and API together; one place for docs and issues | CI must run only the side that changed |
| Two repos | Fully independent release cycles | Cross-cutting changes need two PRs kept in sync; docs split |

## Consequences

- CI workflows use path filters (`client/**`, `server/**`) so each side only builds when it changes.
- Git hooks (husky) are installed from `client/` but live at the repo root `.git`.
- If the teams or release cycles split later, `server/` can move to its own repo with `git filter-repo` and no code changes.

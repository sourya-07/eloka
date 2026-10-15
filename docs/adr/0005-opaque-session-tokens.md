# ADR-0005: Opaque, database-backed session tokens

- **Status:** accepted
- **Date:** 2026-10-15
- **Deciders:** project owner

## Context

The client stores tokens in `sessionStorage` and sends them as `Authorization: Bearer`. It never decodes them. Users must be logged out instantly when deactivated or when their role changes. The blueprint (`onboarding/SECURITY.md` §3) proposed a cookie + BFF for Local Admin applicants.

## Decision

- Access and refresh tokens are 256-bit random values (`elk_at_…`, `elk_rt_…`). Only their SHA-256 hash is stored, in `auth_session`. No JWT.
- Sessions have a sliding idle timeout and an absolute lifetime (configurable). Refresh rotates both tokens; reusing an old refresh token revokes the session.
- Local Admin applicants and Eko staff use the same mechanism with a different `session kind`, each valid only on its own routes. Step-up OTP protects sensitive applicant actions (bank, e-sign, withdraw).
- `org_token` (public, identifies a tenant to the login screen) is a short HMAC-signed value, not a session.

## Consequences

- Revocation is a single row update; no signing keys to rotate for sessions.
- One primary-key lookup per request (with throttled `last_seen_at` writes).
- Deviation from the blueprint's cookie BFF for applicants is accepted: the org-admin token already lives in `sessionStorage`; a BFF can be added later without server changes.

# ADR-0007: TLS edge with Caddy and on-demand certificates

- **Status:** accepted
- **Date:** 2026-10-16
- **Deciders:** project owner (P6 plan)

## Context

Every tenant gets `<label>.<root>` and may bring its own domain (go-live step "Domain configuration": the partner points an A record at our server). Each hostname needs HTTPS. A wildcard certificate covers `*.<root>` but needs DNS-API access for the ACME challenge and does not cover custom domains. Issuing a certificate per hostname by hand does not scale and breaks go-live automation.

## Decision

- One edge container, **Caddy 2** (`deploy/Caddyfile`), in front of `client` and `server`. Only it publishes ports 80/443.
- `api.<root>` → `server:8080`; every other hostname → `client:3000`.
- Tenant certificates use **on-demand TLS**: on the first HTTPS request for a hostname, Caddy asks `GET http://server:8080/v1/system/tls-allowed?domain=<host>` and requests a Let's Encrypt certificate only on `200` (an active tenant domain). Unknown names get no certificate, so the edge cannot be used to exhaust ACME rate limits.
- Certificates are kept in the `caddy-data` volume.
- The client's server-side proxy calls the server on the internal network (`API_INTERNAL_BASE_URL`), not through the edge.

## Consequences

- A new tenant or custom domain works as soon as its DNS points at the host and its hostname is in `org_domain`: no ops ticket for TLS.
- The first request to a new hostname waits a few seconds for issuance.
- The server must be up for new certificates (existing ones keep working).
- CORS still lists allowed origins in configuration; custom domains need adding there until CORS reads tenant domains (runbook "Known gaps").
- Alternatives considered: nginx + certbot (manual per domain), cloud load balancer certificates (ties us to a provider while the deploy target is open), wildcard via DNS-01 (no custom domains).

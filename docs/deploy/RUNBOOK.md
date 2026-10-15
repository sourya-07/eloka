# Deployment runbook

> Status: server stack (P1). The client container, TLS edge and backups are added in P6.

## What runs

| Service | Image | Notes |
|---|---|---|
| `mysql` | `mysql:8.4` | data in volume `mysql-data`; not published |
| `migrate` | `eloka-server` (profiles `prod,migrate`) | runs Flyway once, validates, exits 0. The API never changes the schema |
| `server` | `eloka-server` (profile `prod`) | API on 8080, health on 8081 (`/actuator/health/liveness`, `/readiness`); logs in volume `server-logs` |

Definition: [`deploy/docker-compose.yml`](../../deploy/docker-compose.yml).

## First deployment

1. Install Docker + Compose on the host. Clone the repo (or copy `deploy/` and `server/`).
2. `cp deploy/.env.example deploy/.env` and fill every value. Generate each key with `openssl rand -base64 32`; never reuse keys between environments.
3. Build and start:
   ```bash
   ELOKA_VERSION=1.0.0 docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait
   docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps     # migrate: Exited (0); server: healthy
   ```
4. Put a TLS reverse proxy (nginx/Caddy) in front of port 8080. Log `$uri`, never `$request_uri` (query strings carry personal data). Do not expose `/actuator`.
5. Create the first tenant (provisioning arrives in Release 2; until then ask the backend team).

## Production vs staging

`ELOKA_ENVIRONMENT=production` turns on `ProductionSafetyGuard`. The server **refuses to start** and lists the reasons if any of these hold:
- a development key or secret from `application-local.yaml` is used;
- `ELOKA_SMS_PROVIDER=log` or `ELOKA_EMAIL_PROVIDER=log` (users could not receive OTPs) or `ELOKA_SMS_REVEAL_SECRETS=true`;
- local-only beans are present (fixed OTP, seeder).

Until a real SMS provider is integrated, run as `ELOKA_ENVIRONMENT=staging` and read OTPs from the app log only if `ELOKA_SMS_REVEAL_SECRETS=true` (staging only).

## Routine deployment

```bash
git pull
ELOKA_VERSION=<new> docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait
```
Migrations are backwards compatible (expand/contract), so the previous image keeps working until the new one is healthy.

## Rollback

`ELOKA_VERSION=<previous> docker compose ... up -d --wait`. Never edit or delete a migration that has run; fix forward with a new one.

## Logs

```bash
docker compose -f deploy/docker-compose.yml exec server ls /app/logs     # eloka-app.log, eloka-access.log, eloka-audit.log
docker compose -f deploy/docker-compose.yml logs -f server              # JSON (ECS) on stdout as well
```
Rotation: daily or 100 MB, gzip; kept 30 (app), 90 (access), 365 (audit) days. Personal data is masked.

## Environment variables (server)

See `deploy/.env.example` for the full list with notes. Required: `ELOKA_DB_*`, `ELOKA_CRYPTO_FIELD_KEY`, `ELOKA_CRYPTO_INDEX_KEY`, `ELOKA_ORG_TOKEN_KEY`, `ELOKA_ORG_META_SECRET`, `ELOKA_OTP_PEPPER`, `ELOKA_PLATFORM_ROOT_DOMAIN`, `ELOKA_CORS_ALLOWED_ORIGIN_PATTERNS`. Optional: `ELOKA_ENVIRONMENT`, `ELOKA_SMS_PROVIDER`, `ELOKA_SMS_REVEAL_SECRETS`, `ELOKA_EMAIL_PROVIDER`, `ELOKA_STORAGE_PATH` (default `/app/data/files`, keep it on the `server-files` volume and back it up), `ELOKA_SESSION_IDLE_TIMEOUT`, `ELOKA_SESSION_ABSOLUTE_TTL`, `ELOKA_TRUSTED_PROXIES`, `MANAGEMENT_SERVER_PORT`.

The client must use the same `ELOKA_ORG_META_SECRET` as its `ORG_META_SERVICE_SECRET`, `NEXT_PUBLIC_API_BASE_URL=https://<api host>`, `ORG_META_SERVICE=<internal server URL>/`, and in production `NEXT_PUBLIC_DISABLE_TRANSACTIONS=true`.

# Deployment runbook

> Status: full stack (P6, 16 October 2026). Rehearsed end to end on Docker Desktop with staging settings: start, first tenant, login through the website, TLS gating, backup, restore. Not yet run on a real host with public DNS.

## What runs

Everything is in `deploy/docker-compose.yml`. Only the edge publishes ports.

| Service | Image | Notes |
|---|---|---|
| `edge` | `caddy:2.10-alpine` | ports 80/443. `api.<root>` → server; every other hostname → client. Automatic HTTPS (Let's Encrypt). Tenant certificates are issued on demand, only for hostnames the server confirms (`GET /v1/system/tls-allowed`). Certificates live in volume `caddy-data`: keep it |
| `client` | `eloka-client` | Next.js on 3000 (`/api/health`). Calls the server on the internal network (`API_INTERNAL_BASE_URL`, `ORG_META_SERVICE`) |
| `server` | `eloka-server` (profile `prod`) | API on 8080, health on 8081 (`/actuator/health/liveness`, `/readiness`, not reachable from outside); logs in volume `server-logs`, encrypted documents in `server-files` |
| `migrate` | `eloka-server` (profiles `prod,migrate`) | runs Flyway once, validates, exits 0. The API never changes the schema |
| `mysql` | `mysql:8.4` | data in volume `mysql-data`; not published |
| `bootstrap` | `eloka-server` (profiles `prod,bootstrap`) | tool, not started by `up`: creates the first tenant (below) |

In the commands below, `DC` means `docker compose -f deploy/docker-compose.yml --env-file deploy/.env`.

## First deployment

1. Host: Linux with Docker + Compose, ports 80 and 443 open. Clone the repo.
2. DNS: A records for `api.<root>` and `*.<root>` pointing at the host (and later one per custom domain).
3. `cp deploy/.env.example deploy/.env`, `chmod 600 deploy/.env`, fill every value. Generate each key with `openssl rand -base64 32`; never reuse keys between environments. **Store a copy of `deploy/.env` in the secrets manager**: without `ELOKA_CRYPTO_FIELD_KEY` and `ELOKA_CRYPTO_INDEX_KEY`, backups cannot be read.
4. Build and start:
   ```bash
   ELOKA_VERSION=1.0.0 $DC up -d --build --wait
   $DC ps          # migrate: Exited (0); mysql, server, client: healthy; edge: up
   ```
   `NEXT_PUBLIC_API_BASE_URL` is compiled into the client image as `https://api.<root>`. Changing `ELOKA_PLATFORM_ROOT_DOMAIN` means rebuilding the client image (`--build`).
5. Create the first tenant (Release 2 provisioning will replace this):
   ```bash
   $DC --profile tools run --rm \
     -e ELOKA_BOOTSTRAP_SUBDOMAIN=shlok \
     -e ELOKA_BOOTSTRAP_ORG_NAME="Shlok Enterprise" \
     -e ELOKA_BOOTSTRAP_ADMIN_MOBILE=9xxxxxxxxx \
     -e ELOKA_BOOTSTRAP_ADMIN_NAME="Shlok Goswami" \
     bootstrap
   ```
   Optional: `ELOKA_BOOTSTRAP_APP_NAME`, `ELOKA_BOOTSTRAP_ADMIN_EMAIL`, `ELOKA_BOOTSTRAP_EXTRA_HOSTNAMES` (comma-separated custom domains), `ELOKA_BOOTSTRAP_SUPPORT_EMAIL`, `ELOKA_BOOTSTRAP_SUPPORT_PHONE`. It is safe to re-run: an existing sub-domain is left as it is. The Local Admin then logs in at `https://shlok.<root>` with an OTP.
6. Smoke test: open `https://<label>.<root>`, log in, check `$DC logs server | grep -i error` is quiet.

## Production vs staging

`ELOKA_ENVIRONMENT=production` turns on `ProductionSafetyGuard`. The server **refuses to start** and lists the reasons if any of these hold:
- a development key or secret from `application-local.yaml` is used;
- `ELOKA_SMS_PROVIDER=log` or `ELOKA_EMAIL_PROVIDER=log` (users could not receive OTPs) or `ELOKA_SMS_REVEAL_SECRETS=true`;
- local-only beans are present (fixed OTP, seeder).

Until a real SMS provider is integrated, run as `ELOKA_ENVIRONMENT=staging` and read OTPs from the app log only if `ELOKA_SMS_REVEAL_SECRETS=true` (staging only): `$DC logs server | grep "code for"`.

## Known gaps before production

- **SMS and e-mail providers:** none integrated yet, so production refuses to start (above).
- **Agent onboarding providers:** DigiLocker and agreement e-sign have no real provider. Outside the `local` and `test` profiles both steps answer `PROVIDER_NOT_CONFIGURED` ("not available yet"), so in staging and production a distributor cannot pass DigiLocker and no agent can sign the agreement, which means no new agent finishes onboarding. This includes agents added by an admin through Onboard Agents: they start the same KYC steps. Fix: integrate the providers behind `integration/DigiLockerProvider` and `integration/EsignProvider`.
- **Local Admin applicant API (P7):** PAN and bank-account verification have no real provider either (`PanVerificationProvider`, `BankAccountVerifier`), and the agreement text is a placeholder until legal provides the template (OQ-B10). Outside local/test these steps answer `PROVIDER_NOT_CONFIGURED`, so no applicant can finish yet.
- **Custom domains and CORS:** the browser calls `api.<root>` directly for some requests (menu, logout). The server allows only the origins in `ELOKA_CORS_ALLOWED_ORIGIN_PATTERNS`, so a custom domain must be added there too (and the server restarted) until CORS reads the tenant domains.
- **Not rehearsed on a real host:** Let's Encrypt issuance and DNS were not tested (the drill used `ELOKA_CADDY_GLOBAL=local_certs`).

## Routine deployment

```bash
git pull
ELOKA_VERSION=<new> $DC up -d --build --wait
```
Migrations are backwards compatible (expand/contract), so the previous image keeps working until the new one is healthy. Take a backup first (below).

## Rollback

`ELOKA_VERSION=<previous> $DC up -d --wait`. Never edit or delete a migration that has run; fix forward with a new one. If a release damaged data, restore the backup taken before it.

## Backups

```bash
deploy/backup.sh                     # → deploy/backups/eloka-db-<STAMP>.sql.gz, eloka-files-<STAMP>.tar.gz, eloka-<STAMP>.sha256
```
- Consistent MySQL dump without downtime (`--single-transaction`) plus the encrypted documents volume. Files are `chmod 600`; old ones are deleted after `ELOKA_BACKUP_KEEP_DAYS` (default 14).
- Run it daily from cron, and copy `deploy/backups/` off the host (object storage), e.g. `0 2 * * * cd /srv/eloka && deploy/backup.sh && rclone copy deploy/backups remote:eloka-backups`.
- Keep the keys (`deploy/.env`) in the secrets manager, **not** with the backups.

## Restore

```bash
deploy/restore.sh deploy/backups 20261016T020000Z
```
Checks the checksums, asks you to type `restore`, stops edge/client/server, recreates the database from the dump, replaces the documents, re-runs migrations and starts everything (`--wait`). It needs the same `deploy/.env` the backup was made with. Restore drill (do it after every infrastructure change, at least quarterly): restore the latest backup into a staging copy and log in.

Both scripts take `ELOKA_ENV_FILE` (default `.env`) and `ELOKA_COMPOSE_PROJECT` to target another stack, and `ELOKA_RESTORE_CONFIRM=yes` skips the prompt (automation only).

## Load test

`server/load/home.js` (k6) replays the calls every home page makes after login. Locally (16 Oct 2026, one laptop, 50 virtual users): about 1,080 requests/s, p95 59 ms, 0 errors. Against staging:
```bash
BASE_URL=https://api.<root> SUBDOMAIN=<label> ORG_SECRET=<ELOKA_ORG_META_SECRET> MOBILES=<comma list> OTP=<from the log> k6 run server/load/home.js
```
Login itself is rate limited per mobile and IP (by design), so the script logs each user in once.

## Logs

```bash
$DC exec server ls /app/logs          # eloka-app.log, eloka-access.log, eloka-audit.log
$DC logs -f server                    # JSON (ECS) on stdout as well
$DC logs -f edge                      # access log of the edge (JSON)
```
Rotation: daily or 100 MB, gzip; kept 30 (app), 90 (access), 365 (audit) days. Personal data is masked. Every response carries `X-Request-Id`; grep it in all three files to follow one request.

## Environment variables

See `deploy/.env.example` for the full list with notes. Required: `ELOKA_DB_*`, `MYSQL_ROOT_PASSWORD`, `ELOKA_CRYPTO_FIELD_KEY`, `ELOKA_CRYPTO_INDEX_KEY`, `ELOKA_ORG_TOKEN_KEY`, `ELOKA_ORG_META_SECRET`, `ELOKA_OTP_PEPPER`, `ELOKA_PLATFORM_ROOT_DOMAIN`, `ELOKA_CORS_ALLOWED_ORIGIN_PATTERNS`, `ELOKA_ACME_EMAIL`. Optional: `ELOKA_ENVIRONMENT`, `ELOKA_SMS_PROVIDER`, `ELOKA_SMS_REVEAL_SECRETS`, `ELOKA_EMAIL_PROVIDER`, `ELOKA_STORAGE_PATH` (default `/app/data/files`, on the `server-files` volume), `ELOKA_SESSION_IDLE_TIMEOUT` (2h), `ELOKA_SESSION_ABSOLUTE_TTL` (12h), `ELOKA_ACCESS_TOKEN_TTL` (30m; the client rotates the token with the refresh token before it expires), `ELOKA_TRUSTED_PROXIES` (default: private networks, which covers the edge and the client container), `MANAGEMENT_SERVER_PORT`, `ELOKA_ORG_CACHE_EXPIRY_SEC`, `ELOKA_CADDY_GLOBAL`.

The compose file wires the client for you: `ORG_META_SERVICE_SECRET` = `ELOKA_ORG_META_SECRET`, `API_INTERNAL_BASE_URL` and `ORG_META_SERVICE` = the internal server URL, `NEXT_PUBLIC_API_BASE_URL` = `https://api.<root>`, `NEXT_PUBLIC_DISABLE_TRANSACTIONS=true`.

## CI checks that guard a release

`.github/workflows/ci.yml` (lint, tests, build per side), `security.yml` (gitleaks on the history, Trivy on both images, dependency review on pull requests), `codeql.yml`, and Dependabot (`.github/dependabot.yml`). There is no CD pipeline yet: images are built on the host, because the deploy target is still open.

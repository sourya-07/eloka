# ADR-0006: Logging, secrets and production safety

- **Status:** accepted
- **Date:** 2026-10-15
- **Deciders:** project owner

## Decision

**Logs**
- Three rolling files under `ELOKA_LOG_DIR` (`server/logs` locally): `eloka-app.log` (30 days), `eloka-access.log` (one line per request, 90 days), `eloka-audit.log` (365 days; the `audit_event` table is the system of record).
- Local: readable text + coloured console. Production: JSON (Spring Boot structured logging, ECS format) to the files and stdout.
- Every line carries `request_id` (from/echoed as `X-Request-Id`), and when known `org_id`, `user_code`, `interaction`.
- Request/response bodies are never logged. A masking layer redacts mobiles, PAN, Aadhaar, account numbers, OTPs and tokens as a safety net.

**Secrets**
- Keys (field encryption, blind index, OTP pepper, org-token key, org-meta secret) and DB credentials come from environment variables or Docker secrets. `application-local.yaml` holds well-known *development* values only.

**Production safety**
- `ProductionSafetyGuard` refuses to start when `eloka.environment=production` and any development-only behaviour is present: fixed OTP, revealed OTPs in logs or responses, the local seeder, the dev reset endpoint, or the published development keys.

# PayBridge Production Operations

This document defines the minimum monitoring, logging, alerting, and rollback process for safe production deploys.

## 1) Monitoring Baseline

Enable production profile:

```bash
SPRING_PROFILES_ACTIVE=prod,vault,smtp
```

Expose and scrape these actuator endpoints:

- `/actuator/health`
- `/actuator/metrics`
- `/actuator/prometheus`

Track these high-signal metrics first:

- HTTP request rate and latency (p95/p99)
- HTTP 5xx error rate
- DB connection pool saturation
- JVM memory/GC pressure
- Payment create success/failure ratio
- Webhook processing success/failure ratio

## 2) Logging Baseline

Required logging behavior:

- Keep `INFO` for business flow logs in production.
- Keep stack traces available for errors.
- Never log raw provider secrets, API keys, JWTs, or passwords.
- Include trace/span IDs (MDC fields) when available.

Current code already avoids raw service-argument logging in `LoggingConfiguration`.

## 3) Alerting Policy

Use `docs/alerts/paybridge-prometheus-alerts.yml` as the starter alert rules.

Alert priorities:

- `P1`: API unavailable, sustained 5xx surge, or health probe down.
- `P2`: elevated latency, webhook failure spikes, or high memory pressure.
- `P3`: warning trends (growing 4xx validation volume, slow degradation).

Routing expectation:

- `P1`: immediate page/phone.
- `P2`: Slack + on-call acknowledgement.
- `P3`: ticket/backlog with owner.

## 4) Release Checklist (Pre-Deploy)

1. Run tests:
   - `./mvnw test`
2. Confirm environment variables are set (DB/Redis/RabbitMQ/RSA/Mail/Vault/Stripe webhook secret).
3. Confirm Liquibase migrations are backward-compatible for the target deployment.
4. Confirm webhook endpoints are reachable from provider dashboards.
5. Confirm dashboards/alerts are loaded and active before traffic cutover.

## 5) Post-Deploy Smoke Checks (First 10 Minutes)

1. `GET /actuator/health` returns `UP`.
2. Login + `GET /api/v1/auth/me` works.
3. Provider list/settings endpoint works for an authenticated merchant.
4. Payment creation works using `x-api-key` + `Idempotency-Key`.
5. One webhook event is received and processed successfully.
6. No sustained 5xx spike in logs/metrics.

## 6) Rollback Plan

Use immutable deploy artifacts and versioned tags (example: `paybridge-backend:2026-03-03.1`).

### Trigger rollback if any is true

- API health remains `DOWN` for > 2 minutes.
- 5xx rate exceeds 5% for > 5 minutes.
- Payment creation failure rate exceeds 10% for > 5 minutes.
- Webhook processing consistently fails after deploy.

### Rollback steps

1. Freeze new rollout immediately (stop progressive traffic increase).
2. Redeploy last known good backend artifact.
3. Verify health endpoints and core API smoke checks.
4. Confirm payment + webhook flow recovery.
5. Keep failed release artifact and logs for incident analysis.
6. Announce status in incident channel with timestamps and impact window.

### Database migration safety rules

- Prefer expand/contract migrations.
- Do not deploy destructive schema changes in same release as app logic relying on them.
- If rollback may encounter incompatible schema, use feature flags or two-phase deploy.

## 7) Incident Notes Template

- Start time:
- Detection method (alert name):
- User impact:
- Endpoints affected:
- Rollback executed at:
- Recovery verified at:
- Root cause summary:
- Preventive action items:

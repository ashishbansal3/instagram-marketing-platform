# apps/api — RozPost production API (Spring Boot Java 21)

Layered packages: `domain` · `application` · `infrastructure` · `interfaces`.

## HTTP (`/api/v1`)

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/v1/onboarding` | Tenant + client from web form fields |
| `GET` | `/api/v1/onboarding/{clientId}/status` | Onboarding status |
| `GET/POST` | `/webhooks/whatsapp` and `/api/v1/webhooks/whatsapp` | Meta hub + ingest |
| `POST` | `/api/v1/internal/simulate-wa-message` | **META_MODE=mock only** |
| `GET` | `/api/v1/health` | Liveness |

## Production controls

- **HMAC** `X-Hub-Signature-256` required when `META_MODE=live` (secret from `META_APP_SECRET`); raw body cached in filter before JSON parse.
- **Hub verify** constant-time compare of `hub.verify_token`.
- **Idempotency** via `webhook_receipts` + `jobs.idempotency_key` + unique `media_assets.wa_message_id`.
- **Retries** encoded as `jobs.max_attempts` / stream `maxAttempts` for workers (no blind WA send retry).
- **Correlation** `X-Correlation-Id` → MDC `correlationId` / `tenantId`.
- **Domain events** `MediaReceived`, `JobQueued`, `Approved`.
- **Channels** `ChannelMediaParser` + `BrandKitFactory` ports (OCP).
- **Flyway** `V1` schema · `V2` local seed · `V3` receipts + media columns.

## Simulate intake (mock Meta)

```bash
export META_MODE=mock WEBHOOK_VERIFY_TOKEN=local-verify META_APP_SECRET=local-secret
# after compose up (seed client 22222222-… exists) OR after POST /api/v1/onboarding:
./scripts/e2e-simulate-wa-intake.sh 22222222-2222-2222-2222-222222222222 "Soft beige background"
./scripts/e2e-wa-webhook-ingest.sh
```

```bash
cd apps/api && ./gradlew test
```

## Checklist alignment (`agent-practice-checklist` api + WhatsApp HMAC practices)

- Layers + `/api/v1` + health probe
- Raw-body HMAC → then JSON; constant-time hub verify
- Idempotent durable upsert then 200
- Tenant from WA map (never trust client `tenant_id`)
- ProblemDetail typed errors; AES-GCM token vault
- Tests: signature reject, duplicate receipt, HMAC round-trip, cipher AAD

# RozPost architecture skeleton

Production-oriented layering applied in the monorepo bootstrap.

Companions: [`rozpost-production-best-practices.md`](./rozpost-production-best-practices.md) · [`agent-practice-checklist.md`](./agent-practice-checklist.md) · [`instagram-platform-technical-design.md`](./instagram-platform-technical-design.md)

## Modules

| Module | Layers / patterns |
| --- | --- |
| `apps/api` | `domain` · `application` · `infrastructure` · `interfaces` |
| `apps/worker-media` | Strategy steps (`enhance`, `caption`) via `MediaPipelineStep` |
| `apps/worker-publish` | `PublishStrategy` + `QuotaGovernor` (live limit, never hardcode) |
| `apps/remotion-service` | Internal-token HTTP assemble; no Meta tokens |
| `apps/web` | App Router metadata / sitemap / robots; `/api/v1` client |

## Cross-cutting

- **API versioning:** `/api/v1/*`
- **Correlation ID:** `X-Correlation-Id` → MDC `correlationId` (+ `tenantId` when resolved)
- **WhatsApp HMAC:** raw-body `X-Hub-Signature-256` filter in `live` mode before JSON parse
- **Token vault:** AES-GCM (`TOKEN_ENCRYPTION_KEY`) — never Redis plaintext
- **Domain events (bus stub):** `MediaReceived`, `Approved` (+ planned `MediaProcessed`, `Published`, `ReminderSent`)
- **Config:** env via `rozpost.*` / `.env.example`
- **Migrations:** Flyway only under `apps/api/src/main/resources/db/migration`

## Open/Closed

New media providers, channels, and approval flows plug in via interfaces/strategies — do not edit existing implementations to add a provider.

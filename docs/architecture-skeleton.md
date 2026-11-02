# RozPost architecture skeleton

Production-oriented layering applied in the monorepo bootstrap.

## Modules

| Module | Layers |
| --- | --- |
| `apps/api` | `domain` · `application` · `infrastructure` · `interfaces` |
| `apps/worker-media` | Strategy steps (`enhance`, `caption`) via `MediaPipelineStep` |
| `apps/worker-publish` | `PublishStrategy` (mock behind `META_MODE=mock`) |

## Cross-cutting

- **API versioning:** `/api/v1/*`
- **Correlation ID:** `X-Correlation-Id` → MDC `correlationId`
- **Domain events (bus stub):** `MediaReceived`, `Approved` (+ planned `MediaProcessed`, `Published`, `ReminderSent`)
- **Config:** env via `rozpost.*` / `.env.example`
- **Migrations:** Flyway only under `apps/api/src/main/resources/db/migration`

## Open/Closed

New media providers, channels, and approval flows plug in via interfaces/strategies — do not edit existing implementations to add a provider.

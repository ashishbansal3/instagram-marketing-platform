# RozPost

**RozPost** — WhatsApp-in → AI polish → HITL approve → Instagram out for Indian local businesses. Official Meta APIs only.

Production monorepo (Postgres + Redis + MinIO/S3 + API + workers + Remotion + Next.js).

**GitHub:** https://github.com/ashishbansal3/instagram-marketing-platform

## Quick start (local compose)

```bash
cp .env.example .env
docker compose up --build
```

| Service | URL |
| --- | --- |
| Web (Next.js) | http://localhost:3000 |
| API (Spring Boot) | http://localhost:8080 |
| Remotion | http://localhost:3100 |
| MinIO console | http://localhost:9001 |
| Postgres | localhost:5432 |
| Redis | localhost:6379 |

```bash
META_MODE=mock   # mock | live — mocks only behind this flag; default mock for local
APP_ENV=local    # local | staging | production
```

WhatsApp webhook (dual paths):  
`POST http://localhost:8080/webhooks/whatsapp` · `POST http://localhost:8080/api/v1/webhooks/whatsapp`  
Hub verify: `GET …/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$WEBHOOK_VERIFY_TOKEN&hub.challenge=ok`  
Onboarding: `POST /api/v1/onboarding` · Health: `GET /api/v1/health`

```bash
./scripts/e2e-smoke.sh
# META_MODE=mock only — uses seed client 22222222-…:
./scripts/e2e-simulate-wa-intake.sh
./scripts/e2e-wa-webhook-ingest.sh
```

## Layout

```
apps/web                 Next.js (TypeScript, strict) — marketing + dashboard
apps/api                 Spring Boot Java 21 — layered domain/application/infrastructure/interfaces
apps/worker-media        Kotlin Spring Boot — media pipeline strategies
apps/worker-publish      Kotlin Spring Boot — IG Content Publishing + quota governor
apps/remotion-service    Node Remotion microservice
packages/meta-mocks      Shared WA / IG fixtures (META_MODE=mock only)
```

## Docs

- [`docs/rozpost-production-best-practices.md`](docs/rozpost-production-best-practices.md)
- [`docs/agent-practice-checklist.md`](docs/agent-practice-checklist.md)
- [`docs/architecture-skeleton.md`](docs/architecture-skeleton.md)

## Architecture notes

- Layered packages: `domain` · `application` · `infrastructure` · `interfaces`
- API versioning: `/api/v1/*` from day one
- Correlation ID: `X-Correlation-Id` request filter → MDC `correlationId`
- Domain events (bus): `MediaReceived`, `MediaProcessed`, `Approved`, `Disapproved`, `Published`, `ReminderSent`
- Media steps via Strategy interfaces: enhance · caption · cut · publish
- Publish targets use Strategy (`INSTAGRAM` now; `FACEBOOK` / `TIKTOK` stubs for Open/Closed extension)
- DB: Flyway versioned migrations only (`apps/api/src/main/resources/db/migration`)
- Config: env-externalized (see `.env.example`); no secrets in repo

## META_MODE

| Value | Behavior |
| --- | --- |
| `mock` | Use `packages/meta-mocks` fixtures + stubs (local/dev only) |
| `live` | WhatsApp Cloud API + Instagram Graph with tokens from env |

## Approval + publish

```
intake_received → media_processing → drafted → awaiting_approval
  ├─ approve → publish_queued → publishing → published   (+ Published event)
  └─ disapprove (+ text) → retry ≤3 → abandoned_needs_new_media
```

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/onboarding` | Tenant/client + IG connection row |
| `POST` | `/api/v1/simulations/wa-message` | Mock-mode WA media+instructions (`META_MODE=mock`) |
| `POST` | `/api/v1/approvals/wa-button` | `{ postId, button: approve\|disapprove, feedbackText? }` |
| `GET` | `/api/v1/posts/{id}` | Status / permalink |

Schema: Flyway `apps/api/src/main/resources/db/migration` (`V3__approval_publish.sql` additive).

## Smoke E2E

```bash
chmod +x scripts/e2e-smoke.sh
./scripts/e2e-smoke.sh
# optional disapprove path:
SMOKE_DISAPPROVE_ONCE=1 ./scripts/e2e-smoke.sh
```

Exact curl flow:

```bash
API=http://localhost:8080
curl -sS -X POST $API/api/v1/onboarding -H 'Content-Type: application/json' \
  -d '{"businessName":"Salon","phoneE164":"+919876543210"}'
curl -sS -X POST $API/api/v1/simulations/wa-message -H 'Content-Type: application/json' \
  -d '{"waUserId":"919876543210","instructionText":"Soft beige background"}'
# note postId, then:
curl -sS -X POST $API/api/v1/approvals/wa-button -H 'Content-Type: application/json' \
  -d '{"waUserId":"919876543210","postId":"<POST_ID>","button":"approve"}'
curl -sS $API/api/v1/posts/<POST_ID>
```

## Tests

```bash
cd apps/api && /opt/gradle-8.11.1/bin/gradle test --no-daemon
cd apps/worker-publish && /opt/gradle-8.11.1/bin/gradle test --no-daemon
```

## Media pipeline (`apps/worker-media` + `apps/remotion-service`)

Redis **Streams** (`stream.media-download` → `stream.image-enhance` → `stream.reel-render`) with consumer-group ACK, PEL, and `stream.media-dlq` after max attempts; legacy list keys are bridged.  
Enhance uses Photoroom/fal when keys are set (Resilience4j CB/retry/bulkhead per provider); otherwise flag-gated **mock** sharpness + overlay (offline smoke (mock enhance flag)).  
Reels: ffmpeg CLI 9:16 + Remotion captions (Noto Devanagari / Gurmukhi) with ffmpeg+Noto fallback.

### Local (without full compose)

```bash
# Redis required for queue consumers; sync demo curl works with local FS if MinIO is down
redis-server --daemonize yes   # or use compose redis

cd apps/remotion-service && npm install && npm run dev   # :3100
cd apps/worker-media && ./gradlew bootRun                # :8081
```

### Sample curl — process a fixture image

```bash
curl -sS -X POST 'http://localhost:8081/api/v1/media/process' \
  -F 'file=@fixtures/sample-salon.jpg' \
  -F 'instruction_text=ਗਰਮ ਸਟੂਡੀਓ ਲੁੱਕ — ਅੱਜ ਦੀ ਆਫਰ' \
  -F 'locale=pa-IN' \
  -F 'brand_name=Amritsar Glow Salon'
```

Expect `status: awaiting_approval`, preview URLs, and WA mock at `GET /api/v1/media/wa-mocks (only when WA_MOCK_ENABLED=true)`.

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
| MinIO console | http://localhost:9001 (`minioadmin` / `minioadmin`) |
| Postgres | localhost:5432 |
| Redis | localhost:6379 |

```bash
META_MODE=mock   # mock | live — mocks only behind this flag; default mock for local
APP_ENV=local    # local | staging | production
```

WhatsApp webhook: `POST http://localhost:8080/webhooks/whatsapp`  
Hub verify: `GET http://localhost:8080/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$WEBHOOK_VERIFY_TOKEN&hub.challenge=ok`  
Versioned API: `GET http://localhost:8080/api/v1/health`

Smoke ingest (fixture): `./scripts/e2e-smoke.sh`

## Layout

```
apps/web                 Next.js (TypeScript, strict) — marketing + dashboard
apps/api                 Spring Boot Java 21 — layered domain/application/infrastructure/interfaces
apps/worker-media        Kotlin Spring Boot — media pipeline strategies
apps/worker-publish      Kotlin Spring Boot — IG Content Publishing
apps/remotion-service    Node Remotion microservice
packages/meta-mocks      Shared WA / IG fixtures (META_MODE=mock only)
```

## Architecture notes

- Layered packages: `domain` · `application` · `infrastructure` · `interfaces`
- API versioning: `/api/v1/*` from day one
- Correlation ID: `X-Correlation-Id` request filter → MDC `correlationId`
- Domain events (bus stubs): `MediaReceived`, `MediaProcessed`, `Approved`, `Published`, `ReminderSent`
- Media steps via Strategy interfaces: enhance · caption · cut · publish
- DB: Flyway versioned migrations only (`apps/api/src/main/resources/db/migration`)
- Config: env-externalized (see `.env.example`); no secrets in repo

## META_MODE

| Value | Behavior |
| --- | --- |
| `mock` | Use `packages/meta-mocks` fixtures + stubs (local/dev only) |
| `live` | WhatsApp Cloud API + Instagram Graph with tokens from env |

## Clone

```bash
git clone https://github.com/ashishbansal3/instagram-marketing-platform.git
cd instagram-marketing-platform
cp .env.example .env
docker compose up --build
```

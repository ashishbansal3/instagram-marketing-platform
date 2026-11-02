# RozPost

**RozPost** — WhatsApp-in → AI polish → HITL approve → Instagram out for Indian local businesses. Official Meta APIs only.

Monorepo for the MVP local demo (Postgres + Redis + MinIO + API + workers + Remotion + Next.js).

**GitHub:** https://github.com/ashishbansal3/instagram-marketing-platform

## Quick start (docker-compose)

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
META_MODE=mock   # mock | live  (default mock for demo)
```

WhatsApp webhook (mock): `POST http://localhost:8080/webhooks/whatsapp`  
Hub verify: `GET http://localhost:8080/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=demo-verify&hub.challenge=ok`

## Layout

```
apps/web                 Next.js (TypeScript) — RozPost marketing + dashboard shell
apps/api                 Spring Boot Java 21 — webhooks, onboarding, enqueue
apps/worker-media        Kotlin Spring Boot — enhance / reel prep
apps/worker-publish      Kotlin Spring Boot — IG Content Publishing
apps/remotion-service    Node Remotion microservice
packages/meta-mocks      Shared WhatsApp / IG fixture mocks
```

## META_MODE

| Value | Behavior |
| --- | --- |
| `mock` | Use `packages/meta-mocks` fixtures + in-process stubs (demo default) |
| `live` | Call WhatsApp Cloud API + Instagram Graph with tokens from `.env` |

## Clone

```bash
git clone https://github.com/ashishbansal3/instagram-marketing-platform.git
cd instagram-marketing-platform
cp .env.example .env
docker compose up --build
```

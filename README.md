# RozPost

**RozPost** — WhatsApp-in → AI polish → HITL approve → Instagram out for Indian local businesses. Official Meta APIs only.

Production monorepo (Postgres + Redis + MinIO/S3 + API + workers + Remotion + Next.js).

**GitHub:** https://github.com/ashishbansal3/instagram-marketing-platform

## How to run (local mock E2E)

```bash
# 1) Optional local overrides (gitignored). Compose works WITHOUT .env — built-in defaults.
./scripts/bootstrap-env.sh   # copies .env.example → .env if missing
# or: cp .env.example .env

# 2) Start stack (META_MODE=mock, MOCK_AUTO_ENHANCE=false, bucket=rozpost-media)
docker compose up --build

# 3) Happy path: WA mock webhook → media worker (WA download stub) → assets-ready → approve → mock publish
./scripts/e2e-smoke.sh
```

| Service | URL |
| --- | --- |
| Web (Next.js) | http://localhost:3000 |
| API (Spring Boot) | http://localhost:8080 |
| Remotion | http://localhost:3100 |
| MinIO console | http://localhost:9001 (`minioadmin` / `minioadmin`) |

```bash
META_MODE=mock          # mock | live — Graph stubs only when mock
MOCK_AUTO_ENHANCE=false # must stay false for real media-worker/Remotion path
APP_ENV=local
WEBHOOK_VERIFY_TOKEN=local-verify
S3_BUCKET=rozpost-media
POSTGRES_DB=rozpost
SPRING_API_URL=http://api:8080   # web BFF → Spring (set in compose)
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

WhatsApp webhook: `POST http://localhost:8080/webhooks/whatsapp`  
Hub verify: `GET …/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$WEBHOOK_VERIFY_TOKEN&hub.challenge=ok`  
Health: `GET http://localhost:8080/api/v1/health`

## Layout

```
apps/web                 Next.js — marketing + dashboard + BFF
apps/api                 Spring Boot Java 21
apps/worker-media        Kotlin — Streams media pipeline
apps/worker-publish      Kotlin — IG Content Publishing (Streams)
apps/remotion-service    Node Remotion captions/reel
packages/meta-mocks      WA / IG fixtures (META_MODE=mock only)
```

## CI

GitHub Actions (`.github/workflows/ci.yml`):

- secret-scan (rejects committed `.env` and `demo-*` defaults)
- Gradle `test` for `api`, `worker-media`, `worker-publish`
- Next `tsc` + `build` for `web`
- Remotion real ffmpeg/HTTP render smoke
- `e2e-mock` job: `docker compose` + `./scripts/e2e-smoke.sh`

## Docs

- [`docs/deploy.md`](docs/deploy.md) — **AWS deploy** (S3 · ECS Fargate+ALB · RDS · ElastiCache · Amplify · GitHub OIDC)
- [`docs/rozpost-production-best-practices.md`](docs/rozpost-production-best-practices.md)
- [`docs/agent-practice-checklist.md`](docs/agent-practice-checklist.md)
- [`docs/architecture-skeleton.md`](docs/architecture-skeleton.md)

## Production deploy (AWS — scaffolding only)

Primary hosting is **AWS + S3**. Templates under `infra/deploy/aws/` and `.github/workflows/deploy-aws.yml`.

**Order:** migrate → api → workers → remotion → web

## META_MODE

| Value | Behavior |
| --- | --- |
| `mock` | WA Graph download stub + IG publish fixtures (`packages/meta-mocks`) |
| `live` | WhatsApp Cloud API + Instagram Graph with tokens from env |

## Approval + publish

```
intake_received → media_processing → awaiting_approval
  ├─ approve → publish_queued → publishing → published
  └─ disapprove (+ text) → retry ≤3 → abandoned_needs_new_media
```

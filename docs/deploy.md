# RozPost production deploy (AWS)

**Primary hosting decision:** AWS + S3.  
This repo only ships **scaffolding** — no live deploy runs without Ashish’s AWS account, IAM OIDC trust, RDS, ElastiCache, ECS, and S3 bucket.

## Target shape (MVP)

| Concern | Choice | Notes |
| --- | --- | --- |
| Media objects | **Amazon S3** | Tenant-prefixed keys `tenants/{tenantId}/media/...`; **no public write**; IAM least-privilege on ECS task roles; private bucket + CloudFront OAC or signed GET |
| API + workers + Remotion | **ECS Fargate + ALB** (preferred) | One cluster, four services: `api`, `worker-media`, `worker-publish`, `remotion`. ALB health checks on `/api/v1/health` (api/workers) and `/health` (remotion) |
| Postgres | **Amazon RDS PostgreSQL** | Single writer for MVP; Flyway runs **once** pre-deploy |
| Redis | **Amazon ElastiCache (Redis)** | Streams + job queues; cheaper MVP still uses ElastiCache rather than in-process Redis |
| Web (Next.js App Router) | **AWS Amplify Hosting** (simplest SSR) | Optional CloudFront in front later; avoid static-only S3 for App Router SSR/BFF routes |
| CI → AWS | **GitHub Actions + OIDC** | No long-lived `AWS_ACCESS_KEY_ID` in the repo |

App Runner is a lighter alternative for a single container, but **ECS Fargate + ALB** is the documented default so all four JVM/Node services share one cluster, one IAM pattern, and one ALB/target-group health model.

### Cheaper MVP note

If cost must stay minimal: one ECS cluster (Fargate Spot where safe) + **RDS** + **ElastiCache** + one S3 bucket + Amplify for web. Do **not** skip managed Postgres; Redis on ElastiCache cache.t3.micro (or equivalent) is acceptable for early traffic.

## Prerequisites (Ashish)

1. Create AWS account / choose region (default examples: `ap-south-1`).
2. Create S3 media bucket (block public access ON).
3. Create IAM OIDC provider for `token.actions.githubusercontent.com` and a deploy role trusting this repo.
4. Provision RDS Postgres + ElastiCache Redis in a private VPC.
5. Create ECS cluster + ALB + task execution / task roles (see `infra/deploy/aws/`).
6. Put secrets in **Secrets Manager** or **SSM Parameter Store** (Meta tokens, `TOKEN_ENCRYPTION_KEY`, DB password) — never in git.

## Health probes (ALB / ECS)

| Service | Path | Container port |
| --- | --- | --- |
| `apps/api` | `GET /api/v1/health` | 8080 |
| `apps/worker-media` | `GET /api/v1/health` | 8081 |
| `apps/worker-publish` | `GET /api/v1/health` | 8082 |
| `apps/remotion-service` | `GET /health` | 3100 |
| Amplify web | platform health / `/` | 443 |

## Flyway: expand / contract + pre-deploy once

Migrations live only under `apps/api/src/main/resources/db/migration`.

**Rules**

- Run Flyway **once** in a pre-deploy step (`scripts/pre-deploy-migrate.sh`), not from every Fargate replica on boot.
- Prefer **expand/contract**:
  1. **Expand** — additive columns/tables/indexes (`IF NOT EXISTS` / nullable new cols).
  2. Deploy code that dual-writes / dual-reads as needed.
  3. **Contract** — later migration drops obsolete columns after old code is gone.
- Avoid destructive renames in the same release as readers that still need the old shape.
- On long-running API tasks after migrate succeeds, set `SPRING_FLYWAY_ENABLED=false` (or keep enabled only on a one-shot migrate task definition).

Sketch:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://$RDS_ENDPOINT:5432/rozpost
export SPRING_DATASOURCE_USERNAME=...
export SPRING_DATASOURCE_PASSWORD=...   # from Secrets Manager in real CI
./scripts/pre-deploy-migrate.sh
```

## Deploy order

Always:

1. **Migrate** — `scripts/pre-deploy-migrate.sh` against RDS  
2. **API** — ECS service `rozpost-api` (ALB target group healthy on `/api/v1/health`)  
3. **Workers** — `worker-media` then `worker-publish` (consume Redis Streams only after API/schema ready)  
4. **Remotion** — caption/render service (`/health`)  
5. **Web** — Amplify (points `SPRING_API_URL` / `NEXT_PUBLIC_API_BASE_URL` at the ALB HTTPS URL)

Rollback: revert ECS task definition revision; do **not** auto-down-migrate. Contract migrations are forward-only cleanup.

## S3 IAM (least privilege sketch)

Task role for media worker (and API if it reads objects):

- `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` on `arn:aws:s3:::BUCKET/tenants/*`
- `s3:ListBucket` on `arn:aws:s3:::BUCKET` with prefix condition `tenants/`
- No `s3:*` on `*`
- Bucket: Block Public Access; deny `s3:PutObject` from anonymous / non-role principals

See `infra/deploy/aws/iam-s3-media-policy.json`.

## GitHub Actions OIDC

Workflow sketch: `.github/workflows/deploy-aws.yml`  
Role trust sketch: `infra/deploy/aws/github-oidc-trust-policy.json`

Ashish must create the OIDC provider + role; the workflow assumes the role — **no access keys in git**.

## Not primary

Vercel / Railway / Fly.io / Cloudflare R2 are **not** the production target. Local MinIO remains for `docker compose` only.

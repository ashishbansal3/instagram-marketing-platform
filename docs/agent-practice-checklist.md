# RozPost — per-module production practice checklist

English. Use before merging module work. Sources: `docs/instagram-platform-technical-design.md` §11–12, `docs/rozpost-production-best-practices.md`, `internal/production-build-standards.md`.

Legend: `[ ]` must-do for production MVP · `mock` only behind `META_MODE` / Spring profiles.

---

## infra (compose, CI, secrets, Postgres, Redis, R2, deploy)

- [ ] No user-facing or path names with `demo`; prefer `prod` / production / `e2e-smoke`
- [ ] Secrets via env / host secret store only — nothing on argv or committed `.env`
- [ ] Postgres: Flyway/Liquibase migrations only; expand/contract; pre-deploy once (not per replica)
- [ ] App DB role non-superuser / non-`BYPASSRLS`; RLS plan documented when enabling
- [ ] Redis provisioned for Streams + ShedLock; persistence/eviction understood for PEL/DLQ
- [ ] R2/S3 bucket + tenant key prefix `{tenant_id}/…`; no public write
- [ ] Health endpoints wired for Railway/Fly; overlap + drain / Fly checks configured
- [ ] Vercel project for `apps/web`; Railway/Fly services for api, workers, remotion
- [ ] CI: Gradle test + Next typecheck + migrate dry-run; fail on secret scan
- [ ] Correlation-ID and structured logging sinks (Axiom/Betterstack) + Sentry DSNs present
- [ ] `TOKEN_ENCRYPTION_KEY` (versioned) and Meta/Razorpay secrets in host store

---

## web (`apps/web` — Next.js)

- [ ] TypeScript `strict`; no `any` leaks on API clients
- [ ] `/api/v1` client base URL from env; send/propagate `X-Correlation-Id`
- [ ] App Router `metadata` / `generateMetadata` on public routes; `metadataBase` set
- [ ] `sitemap.ts` + `robots.ts`; dashboard / token links `noindex`
- [ ] `next/image` + `next/font` (Devanagari/Gurmukhi); CWV-conscious hero/LCP
- [ ] Onboarding + OAuth start/callback UX; never display raw Meta tokens
- [ ] Error UI maps typed API `code` — no swallowed fetch failures
- [ ] Feature flags for mock Meta only; production default is live-ready config shape
- [ ] Unit/Playwright smoke for onboarding happy path

---

## api (`apps/api` — Spring Boot Java 21)

- [ ] Package layers: `domain` / `application` / `infrastructure` / `interfaces`
- [ ] All product routes under `/api/v1/…`; `/api/v1/health` for deploy probes
- [ ] SOLID + Strategy/Factory ports for channels, media modes, brand kits
- [ ] Domain events emitted after durable writes (`MediaReceived`, `Approved`, …)
- [ ] WhatsApp: raw-body HMAC `X-Hub-Signature-256`; constant-time; 200 after durable upsert
- [ ] Idempotency on `wamid` / status ids (Postgres unique)
- [ ] Tenant resolution from session / WA map — never trust client `tenant_id` alone
- [ ] Typed errors / ProblemDetail; no empty catches
- [ ] MDC: `correlationId`, `tenantId`; virtual threads OK for IO
- [ ] Meta tokens AES-GCM at rest; refresh job; `needs_reauth` path
- [ ] Enqueue Redis jobs with ids + `correlationId` only (no plaintext tokens)
- [ ] Integration tests: signature reject, duplicate webhook, cross-tenant IDOR
- [ ] Flyway migrations owned/shared correctly with workers

---

## worker-media (`apps/worker-media` — Kotlin/Java)

- [ ] Layered packages; Strategy for enhance / cut / caption selection
- [ ] Redis Stream consumer group; manual ACK; PEL reclaim; max-attempts → DLQ
- [ ] Download WA media first; checkpoint `r2_key` before enhance
- [ ] Resilience4j: **separate** CB/retry/bulkhead per Photoroom, fal, Remotion
- [ ] Fallback ladder to cut-only + captions; no infinite retry on safety rejects
- [ ] Honor `instruction_text` when present; persist cost_ledger rows
- [ ] Emit `MediaProcessed` (or equivalent) after durable preview assets
- [ ] ffmpeg via controlled ProcessBuilder (timeouts, arg allowlist); Remotion over HTTP
- [ ] Logs carry `correlationId`, `tenantId`, `jobId`, `postId`
- [ ] Tests: idempotent re-delivery, CB open → fallback, DLQ transition

---

## worker-publish (`apps/worker-publish` — Kotlin/Java)

- [ ] Quota governor: live `content_publishing_limit` before every `media_publish`
- [ ] No hardcoded quota_total; schedule + notify when near limit
- [ ] Container → poll `FINISHED` → publish; no recreate on in-progress; no blind publish retry
- [ ] Decrypt Meta token only in-process; never log; never Redis plaintext
- [ ] On token failure: `needs_reauth`, stop tenant publish, surface event
- [ ] Idempotency keys for publish steps; mark `Published` domain event once
- [ ] Optional FB cross-post behind interface/Strategy
- [ ] Resilience4j around Graph HTTP; respect 429 Retry-After
- [ ] Plan/past_due gate before publish
- [ ] Tests: quota exhausted schedules; duplicate job does not double-post

---

## remotion-service (`apps/remotion-service` — Node only)

- [ ] Small HTTP API only (assemble captions/reel); no Meta tokens in this service
- [ ] Auth: internal shared secret / mTLS / private network — not public anonymous
- [ ] Noto Devanagari/Gurmukhi fonts bundled; never diffusion-rendered script
- [ ] Timeouts + max concurrent renders; return typed errors to JVM worker
- [ ] `/health` for Fly/Railway; graceful SIGTERM (finish or fail current job cleanly)
- [ ] Input validation (template id, asset URLs allowlisted to R2)
- [ ] No PII in logs beyond job ids; correlation id echoed from request header
- [ ] Smoke test: one template render in CI (or golden-frame check)

---

## Quick reject signals (any module)

- Swallowing exceptions · hardcoded tenants/tokens · `/api` without `v1` for new product routes · webhook JSON parse before HMAC · auto-retry WA send / IG `media_publish` · session-level tenant `SET` on pooled Postgres · plaintext Meta tokens in Redis · “demo” as the default product path

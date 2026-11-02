# RozPost — Production Best Practices (researched)

**Product:** Instagram marketing automation for Indian SMBs (WhatsApp-in → AI → HITL → IG out)  
**Stack:** Next.js (Vercel) · Spring Boot Java 21 API · Kotlin/Java workers · Node Remotion · Postgres · Redis · R2  
**Audience:** Engineering decisions for production MVP (not a demo).  
**Companions:** [`instagram-platform-technical-design.md`](./instagram-platform-technical-design.md) (§11–12) · deep SEO sibling `rozpost-seo-keyword-research.md`  
**Date:** 2 Nov 2026.

---

## How to use this doc

Concise recommendations with links. Locked product decisions live in the technical design §12; this file is the researched rationale agents and Ashish can re-check when Meta/docs drift.

---

## 1) Spring Boot Java 21 + Kotlin media microservices

**Recommendations**

- Run the API on **Spring Boot 3.x / Java 21**; enable virtual threads for webhook and outbound HTTP concurrency.
- Keep **media** and **publish** as separate Spring Boot services (Kotlin preferred for workers) so CPU-heavy ffmpeg/Remotion orchestration does not block webhook ingress.
- Prefer **Redis Streams + consumer groups**, manual `XACK`, pending-entry reclaim (`XPENDING`/`XCLAIM`), and a **DLQ stream** after bounded retries — durable and multi-instance friendly vs fire-and-forget pub/sub.
- Use **Resilience4j 3** (Java 21+): **one circuit-breaker (and retry/bulkhead) instance per remote backend** (Photoroom, fal, Remotion, Graph). Configure aspect order so **CircuitBreaker wraps Retry**.
- Use **ShedLock** (or Redis locks) for cron-style insights/reminders/scheduler so overlapping deploys do not double-fire.
- Remotion stays a **small Node HTTP service**; JVM shells ffmpeg and calls Remotion over HTTP.

**Sources**

- [Resilience4j README — unique instances per backend](https://github.com/resilience4j/resilience4j/blob/master/README.adoc)
- [Resilience4j Spring Boot 3 starter — aspect order & virtual threads](https://github.com/resilience4j/resilience4j/blob/master/resilience4j-spring-boot3/README.adoc)
- Redis Streams consumer-group + DLQ patterns (e.g. [Spring Boot Redis Stream consumer examples](https://github.com/yoanesber/Spring-Boot-Redis-Stream-Consumer))

---

## 2) WhatsApp Cloud API webhooks (HMAC, idempotency, retries)

**Recommendations**

1. **GET verify:** constant-time compare `hub.verify_token`; echo `hub.challenge` as plain text.
2. **POST:** read **raw body bytes** first; verify `X-Hub-Signature-256` (`sha256=` + HMAC-SHA256 with App Secret) with constant-time compare; **then** JSON-parse. A global JSON body parser before the filter breaks signatures.
3. **Ack fast:** return **200** after signature OK + durable idempotent persist/enqueue. Meta retries failed deliveries on backoff for up to ~**7 days**; slow handlers cause duplicate storms.
4. **Idempotency:** dedupe on message/status ids (`wamid`, etc.) in a **shared durable store** (Postgres unique + optional Redis TTL) — in-memory dedupe is unsafe multi-instance.
5. **Outbound caution:** `POST /messages` is **not** safely auto-retried; timeouts may mean the message was already accepted. Prefer explicit ops/alert over blind retry.

**Sources**

- Meta webhook signature model (X-Hub-Signature-256 over raw body) — mirrored in production adapters such as [WhatsAppCore webhooks notes](https://github.com/kearns2000/WhatsAppCore/blob/main/docs/webhooks.md) and [architecture (no auto message retries)](https://github.com/kearns2000/WhatsAppCore/blob/main/docs/architecture.md)
- Ack-before-heavy-work pattern discussion: WhatsApp adapter middleware docs (200 within ~30s budget; Meta multi-day retry window)

**Official docs to re-check before go-live:** [Meta WhatsApp Cloud API Webhooks](https://developers.facebook.com/docs/graph-api/webhooks) · [WhatsApp Cloud API Getting Started](https://developers.facebook.com/docs/whatsapp/cloud-api/get-started)

---

## 3) Instagram Content Publishing — quotas & governor

**Recommendations**

- Always call `GET /{ig-user-id}/content_publishing_limit?fields=quota_usage,config` **before** `media_publish`.
- Trust **live** `config.quota_total`, `quota_usage`, and `quota_duration` (often 86400s). Historical samples commonly show **25**/24h; other OpenAPI fixtures show different totals — **never hardcode**.
- Enforce a product-side governor (schedule + notify) especially for scheduled posts; Meta samples explicitly recommend app-side enforcement.
- Publishing flow: create container → poll until `FINISHED` → `media_publish`. If still processing (e.g. subcode 2207027), **poll again — do not recreate** the container.
- Treat `media_publish` as **create-once**; avoid automatic retries that can duplicate posts. Carousel album counts as **one** publish toward quota in common Graph behavior — still verify live.

**Sources**

- [Meta sample: Reels publishing + content_publishing_limit notes](https://github.com/fbsamples/reels_publishing_apis/tree/main/insta_reels_publishing_api_sample) (25 posts / 24h moving window; enforce in-app for schedules)
- Graph field examples: [restfb discussion — quota_total 25](https://github.com/restfb/restfb/issues/1256)
- Operational checklist style notes: [instagram-mcp publishing tools](https://github.com/IvanBBaev/instagram-mcp/blob/main/docs/tools.md) (runtime quota_total; no blind publish retry)

**Official docs to re-check:** [Instagram Content Publishing](https://developers.facebook.com/docs/instagram-api/guides/content-publishing) · `content_publishing_limit` reference on developers.facebook.com

---

## 4) AI media resilience (circuit breakers, fallbacks, DLQ)

**Recommendations**

- Isolate failure domains: separate CB/timeout/bulkhead for each AI HTTP API and for Remotion.
- **Fallback ladder:** paid enhance → cheaper rembg → **deterministic cut/reframe + captions** so HITL still gets a preview.
- Checkpoint every step (`r2_key`, job status); idempotency key `tenant_id:post_id:step`.
- Retry only transient 429/5xx with exponential backoff + jitter; never retry model safety rejects.
- After max attempts: move to **DLQ**, mark job `dead`, notify owner on WhatsApp, page on-call via Sentry/logs.
- Keep tokens and secrets out of job payloads; pass asset ids + `correlationId` only.

**Sources**

- Resilience4j per-backend isolation (link above)
- Redis Streams DLQ after max delivery count (consumer-group patterns above)

---

## 5) Postgres multi-tenant isolation

**Recommendations**

1. **Shared-schema SaaS:** `tenant_id` on every tenant-owned table; composite uniques `(tenant_id, business_key)`.
2. **Application filter always-on** + automated IDOR tests (cross-tenant GET/PATCH must 404).
3. **RLS when past prototype:** `ENABLE` + `FORCE ROW LEVEL SECURITY`; policy compares `tenant_id` to `current_setting('app.current_tenant_id', true)`.
4. Set tenant with **`set_config('app.current_tenant_id', …, true)`** (transaction-local). Session-level `SET` on pooled connections risks **cross-tenant bleed**.
5. App DB role: **NOSUPERUSER, NOBYPASSRLS**. Superuser/`BYPASSRLS` silently voids RLS.
6. Object storage path isolation: `r2://…/{tenant_id}/…`.

**Sources**

- [AWS / Prisma-style RLS with transaction-local `set_config`](https://github.com/prisma/prisma-client-extensions/blob/main/row-level-security/README.md)
- [OWASP multi-tenant discussion — BYPASSRLS + pool sticky SET hazards](https://github.com/OWASP/CheatSheetSeries/issues/2309)
- [AWS SaaS Factory PostgreSQL RLS sample](https://github.com/aws-samples/aws-saas-factory-postgresql-rls)

---

## 6) OAuth token refresh + secure storage

**Recommendations**

- Encrypt Meta user tokens at rest with **AES-256-GCM**, fresh IV per write, key from env/KMS (versioned keyring). Prefer AAD binding `tenant_id` + provider.
- Store ciphertext in Postgres (`bytea` / envelope string); **never** Redis job bodies or logs.
- Exchange short-lived codes/tokens for long-lived per Meta path:
  - Instagram Login: `ig_exchange_token` / `ig_refresh_token` on `graph.instagram.com`
  - Facebook Login: `fb_exchange_token` / refresh on `graph.facebook.com`
- Proactive refresh before `token_expires_at`; on failure set `needs_reauth` and WhatsApp-notify. Concurrent refresh should be single-flight / row-locked.
- Decrypt only inside publish/insights workers at call time.

**Sources**

- AES-GCM envelope patterns for OAuth vaults (e.g. [token-cipher AES-GCM + AAD](https://github.com/fxylabs/spfn/blob/main/packages/auth/src/server/lib/oauth/token-cipher.ts), [storage guidance](https://github.com/vishhvak/chatgpt-oauth/blob/main/docs/storage.md))
- Meta long-lived token exchange/refresh — re-verify on [Meta Instagram Login](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/business-login) and [Facebook Login long-lived tokens](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived)

---

## 7) Next.js 2025 SEO (brief)

Engineering floor for `apps/web` (keyword research is a sibling doc):

- Use App Router **`metadata` / `generateMetadata`** in Server Components; set `metadataBase`.
- Ship `sitemap.ts`, `robots.ts`, favicon/OG file conventions, per-route canonicals.
- Keep marketing content in **server-rendered HTML**; optimize CWV with `next/image`, `next/font` (include Devanagari/Gurmukhi), lean client bundles.
- `noindex` authenticated dashboard and tokenized analytics links.

**Sources**

- [Next.js Metadata / file conventions](https://nextjs.org/docs/app/getting-started/project-structure#metadata-file-conventions)
- [Vercel next-skills metadata notes](https://github.com/vercel/nextjs-skills/blob/main/skills/next-best-practices/metadata.md)
- Core Web Vitals field targets (LCP ≤ 2.5s, INP ≤ 200ms, CLS ≤ 0.1) — Google Search Central

---

## 8) Vercel + Railway / Fly — zero-downtime

**Recommendations**

| Surface | Practice |
| --- | --- |
| **Vercel (Next.js)** | Git production deploy; PR previews; `promote` / `rollback`; optional rolling release for gradual traffic |
| **Railway (API/workers)** | Configure **healthcheckPath** (e.g. `/api/v1/health`); use overlap + draining seconds so new container becomes Active before old gets SIGTERM; drain ≥ in-flight request/job grace |
| **Fly** | HTTP checks on `/health`; failed checks keep old machines (automatic rollback of bad release) |
| **Migrations** | Run Flyway **once** in pre-deploy; expand/contract schema; never race migrations from N replicas |
| **JVM shutdown** | Honor SIGTERM: stop taking new Redis messages, finish/NACK in-flight, close pool |

**Sources**

- [Railway deployments reference — healthcheck, overlap, draining](https://docs.railway.com/reference/deployments) (also mirrored in railway docs repo)
- Fly HTTP service checks / keep-old-on-failure patterns (Fly.io docs + deploy tool defaults)
- [Vercel deploy / promote / rollback / rolling-release](https://vercel.com/docs/cli/deploying-overview)

---

## Cross-cutting production bar

| Area | Bar |
| --- | --- |
| Config | Env/secrets only; `META_MODE=mock\|live` behind flags — no default “demo” path |
| API | `/api/v1` + RFC 7807-ish typed errors + `X-Correlation-Id` |
| Logging | Structured JSON with `correlationId`, `tenantId`, `jobId` — never tokens |
| Tests | Unit + integration per module; webhook signature + tenant IDOR required |
| Observability | Sentry (JVM + Next) + log sink; alert on DLQ depth and `needs_reauth` |

---

## Changelog

| Date | Change |
| --- | --- |
| 2026-11-02 | Initial researched recommendations for RozPost production MVP |

# RozPost — `apps/web`

Production Next.js (App Router) + TypeScript + Tailwind site: marketing, onboarding, Instagram connect, SEO, and analytics dashboard shell.

**Brand:** RozPost · **Tagline:** WhatsApp photos → Instagram, every day · **Domain:** rozpost.com

## Routes

| Path | Purpose |
| --- | --- |
| `/` | SEO landing (JSON-LD SoftwareApplication + LocalBusiness) |
| `/onboarding` | Business profile → `POST /api/v1/onboarding` |
| `/connect` | Professional guide + Instagram Login (`META_MODE`) |
| `/dashboard` | Analytics KPI shell |
| `/blog`, `/blog/[slug]` | SEO guides for Indian SMBs |
| `/robots.txt`, `/sitemap.xml` | Crawler + sitemap |
| `/api/v1/onboarding` | BFF → Spring or local fallback |
| `/api/v1/oauth/instagram/connect` | Live redirect or mock session (`META_MODE`) |
| `/api/v1/dashboard/summary` | KPI JSON |

## Architecture

```
src/config          Externalized env
src/domain          Onboarding types
src/application     Use-cases / services
src/infrastructure  Local store fallback (only when Spring unset)
src/components      UI
src/app             Next.js App Router (interfaces)
```

Typed `AppError`s, `x-correlation-id` on API responses, `/api/v1` versioning.

## Develop

```bash
cd apps/web
cp .env.example .env.local
npm install
npm run dev
```

## Config

| Var | Meaning |
| --- | --- |
| `NEXT_PUBLIC_SITE_URL` | Canonical / OG base |
| `META_MODE` / `NEXT_PUBLIC_META_MODE` | `mock` \| `live` |
| `SPRING_API_URL` | Forward BFF to Spring Boot |
| `NEXT_PUBLIC_INSTAGRAM_OAUTH_START_URL` | Live OAuth start when Spring unset |

## SEO

Meta title/description, Open Graph, Twitter cards, canonical, JSON-LD, semantic H1/H2, `robots.ts`, `sitemap.ts`. Brand-safe: no guaranteed #1 ranking claims. Deep keyword map applied from `docs/rozpost-seo-keyword-research.md` when present.

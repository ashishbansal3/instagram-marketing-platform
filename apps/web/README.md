# RozPost — `apps/web`

Next.js (App Router) + TypeScript + Tailwind marketing site, onboarding, OAuth connect stub, SEO, and dashboard stub for the Instagram marketing automation MVP.

**Brand:** RozPost · **Tagline:** WhatsApp photos → Instagram, every day · **Domain intent:** rozpost.com

## Routes

| Path | Purpose |
| --- | --- |
| `/` | SEO landing (Instagram promotional / automation India keywords) |
| `/onboarding` | Business profile form → `POST /api/onboarding` |
| `/connect` | Instagram Professional guide + demo OAuth connect |
| `/dashboard` | Analytics placeholders |
| `/blog` | SEO guide index |
| `/blog/[slug]` | 3 articles (salons, clinics, local shops) |
| `/robots.txt` | Crawler rules |
| `/sitemap.xml` | Sitemap |
| `/api/onboarding` | Mock BFF (forwards to Spring when `SPRING_API_URL` set) |
| `/api/oauth/instagram/demo-connect` | Demo fake token mint |
| `/api/dashboard/summary` | KPI placeholder JSON |

## Develop

```bash
cd apps/web
cp .env.example .env.local
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## API proxy / Spring

- Local route handlers under `src/app/api/**` serve the demo when Spring is not ready.
- Set `SPRING_API_URL` to forward onboarding + dashboard summary to the Java API.
- Extra proxy path: `/api/proxy/*` → `$SPRING_API_URL/api/*` (see `next.config.ts`).

## SEO checklist

- Meta title/description, Open Graph, Twitter cards, canonical via `src/lib/seo.ts`
- JSON-LD: `SoftwareApplication` + `LocalBusiness` on the landing page
- Semantic H1/H2, mobile-first hero, `next/font` swap for LCP-friendly text
- `robots.ts` + `sitemap.ts`
- Brand-safe copy: no guaranteed #1 ranking claims

## Demo OAuth

“Connect Instagram (demo)” stores a fake token in `localStorage` (`ig_demo_meta_tokens`) and marks onboarding status `oauth_connected` in the in-memory mock store. Production will redirect to Instagram Business Login.

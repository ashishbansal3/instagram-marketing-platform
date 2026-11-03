---
cursor:
  subagentId: "bc-8ec35330-bb36-5006-9679-60fb787d057c"
---

# SEO apply checklist — `apps/web` (frontend agent)

**Source of truth:** [`../docs/rozpost-seo-keyword-research.md`](../docs/rozpost-seo-keyword-research.md)  
**Tech decisions:** [`../docs/instagram-platform-technical-design.md`](../docs/instagram-platform-technical-design.md) §11  
**Brand:** RozPost — WhatsApp photos → Instagram, every day  
**Scope:** Production Next.js App Router under `apps/web`. English-only UI copy.

Use this as an implementation punch-list. Do not invent alternate primary keywords.

---

## A. Global / layout

- [ ] Root `lang="en"`; wrap HI/PA snippets with `lang="hi"` / `lang="pa"`.
- [ ] Brand-visible hero on `/` (RozPost not just nav).
- [ ] Shared metadata defaults + per-route overrides via Metadata API.
- [ ] Canonical URLs for every public page; absolute `metadataBase` for production domain.
- [ ] Open Graph + Twitter cards (title, description, image 1200×630).
- [ ] `robots.txt`: allow public; disallow `/dashboard`, API internals.
- [ ] `sitemap.xml`: `/`, `/onboarding`, `/connect`, `/blog`, all posts — **exclude** `/dashboard`.
- [ ] JSON-LD on `/`: `SoftwareApplication` (+ optional operator `Organization` / Punjab `LocalBusiness`).
- [ ] Fonts: primary EN display/body via `next/font` (not Inter/Roboto/Arial default stack if design allows expressive choice).
- [ ] Indic: `Noto_Sans_Devanagari` / `Noto_Sans_Gurmukhi`, subsets only, `display: "swap"`, **`preload: false`** unless route shows that script.
- [ ] Analytics deferred (`next/script` afterInteractive/lazyOnload) — never block LCP.

## B. Route copy & headings

### `/`
- [ ] Title ≈ `WhatsApp to Instagram for Local Businesses | RozPost`
- [ ] Meta includes Indian local businesses + Approve + Instagram
- [ ] H1: product benefit (WhatsApp in / Instagram out) — brand adjacent
- [ ] H2s: how it works, verticals, IG+FB, official Meta APIs, pricing
- [ ] FAQ (H3/H4) covering exact seeds: promotional, automatic Instagram handler, automatic Instagram post handler, Instagram automatic Facebook handler, Instagram automation India, Instagram marketing service
- [ ] CTA → `/onboarding`; secondary → `/connect` / `/blog`
- [ ] Primary keyword cluster: WhatsApp→IG + local business marketing (not “scheduler” alone)

### `/onboarding`
- [ ] Title/meta conversion-focused (start Instagram marketing / shop setup)
- [ ] H1 clear next action; H2s for form sections
- [ ] Light keyword density; link to `/connect` as next step
- [ ] Collect phone, business, category, city, bio, optional photo (existing product flow)

### `/connect`
- [ ] Title/meta: Connect Instagram Professional (+ optional Facebook)
- [ ] H1 compliant framing (in-app Professional, then OAuth)
- [ ] `HowTo` JSON-LD for Professional + Connect steps
- [ ] FAQ mention cross-post / “automatic Facebook handler” in plain language
- [ ] Outbound link to official Instagram Help (Professional account)

### `/dashboard`
- [ ] `robots: { index: false, follow: false }`
- [ ] No SEO keyword targeting; fast empty states

### `/blog` + posts
- [ ] Index title/H1 for Indian local-business Instagram guides
- [ ] Ship posts aligned to research outlines (priority):
  1. WhatsApp to Instagram pillar
  2. Salons in Punjab
  3. Clinics without a designer
  4. Auto post vs Meta Business Suite
  5. Instagram Professional setup India
  6. IG+FB automatic cross-post (safe)
  7. Instagram marketing service cost India
  8. Daily posts for retail/furniture
- [ ] Each post: one primary long-tail, CTA → `/onboarding`, 2+ internal links
- [ ] `BlogPosting` JSON-LD; `datePublished` / `dateModified`

## C. On-page mechanics

- [ ] One H1 per page
- [ ] Primary phrase in title, H1 or close paraphrase, first 100 words, one H2, relevant image alt
- [ ] Density ~0.8–1.5% for primary; synonyms over stuffing
- [ ] Descriptive anchors (not “click here”)
- [ ] Image pipeline: `next/image`; hero `priority`; below-fold lazy; width/height to protect CLS
- [ ] No doorway city pages unless unique copy (see research §6)

## D. CWV gate before merge

- [ ] Lighthouse/PageSpeed mobile: LCP < 2.5s, CLS < 0.1, INP good on `/` and one blog post
- [ ] No layout shift from fonts/images
- [ ] Marketing routes statically generated or ISR

## E. Local / off-site (frontend can prep; ops owns GBP)

- [ ] Footer or About: Punjab service-area mention + link to contact/WhatsApp
- [ ] NAP consistency if address shown
- [ ] Provide OG assets + screenshots pack for directory submissions (PH, SaaSHub, etc.)

## F. Out of scope for frontend

- Buying backlinks / PBN
- Unofficial IG/WA automation claims in copy
- Promising API-created Instagram Business accounts
- Full Hindi/Punjabi site IA (unless product later decides)

## G. Done when

- [ ] All public routes match titles/metas/H1 plan above (or documented intentional deltas)
- [ ] FAQ covers the six required seed phrases
- [ ] `/dashboard` noindexed
- [ ] Fonts/images CWV-safe
- [ ] Blog has ≥3 live posts from the outline list (stretch: 8)
- [ ] Cross-link from README or docs index to `docs/rozpost-seo-keyword-research.md`

import Link from "next/link";
import { JsonLd } from "@/components/json-ld";
import { PRODUCT_NAME, TAGLINE } from "@/lib/brand";
import {
  localBusinessJsonLd,
  softwareApplicationJsonLd,
} from "@/lib/seo";

export default function HomePage() {
  return (
    <>
      <JsonLd data={[softwareApplicationJsonLd(), localBusinessJsonLd()]} />

      <section className="hero-wash relative min-h-[88vh] text-white">
        <div className="site-shell flex min-h-[88vh] flex-col justify-end gap-8 pb-16 pt-24 sm:justify-center sm:pb-24">
          <p className="brand-mark fade-up text-4xl tracking-tight sm:text-6xl md:text-7xl">
            {PRODUCT_NAME}
          </p>
          <h1 className="fade-up-delay max-w-3xl text-2xl font-semibold leading-snug tracking-tight sm:text-4xl">
            {TAGLINE}
          </h1>
          <p className="fade-up-delay-2 max-w-xl text-base text-white/85 sm:text-lg">
            Instagram marketing service for Indian salons, clinics, and shops —
            automatic Instagram post handler with WhatsApp intake and your
            approve tap. Roz-roz posting, bina password share.
          </p>
          <div className="fade-up-delay-2 flex flex-wrap gap-3">
            <Link
              href="/onboarding"
              className="inline-flex items-center justify-center rounded-md bg-[var(--accent-soft)] px-5 py-3 text-sm font-semibold text-[var(--brand-deep)] transition hover:bg-white"
            >
              Start free onboarding
            </Link>
            <Link
              href="/#how-it-works"
              className="inline-flex items-center justify-center rounded-md border border-white/35 bg-white/10 px-5 py-3 text-sm font-semibold text-white backdrop-blur transition hover:bg-white/20"
            >
              See how it works
            </Link>
          </div>
        </div>
      </section>

      <section id="how-it-works" className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark max-w-2xl text-3xl text-[var(--brand-deep)] sm:text-4xl">
          WhatsApp se photo, Instagram pe post
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          {PRODUCT_NAME} is an automatic Instagram handler for local businesses —
          promotional posts without living in the Instagram editor.
        </p>
        <ol className="mt-10 grid gap-8 sm:grid-cols-3">
          {[
            {
              t: "1. Send on WhatsApp",
              d: "Owner clicks a photo of the work, product, or storefront and sends it 1:1 on WhatsApp Cloud API.",
            },
            {
              t: "2. Approve the draft",
              d: "AI polishes stills/reels with Hindi/Punjabi-friendly captions. You Approve or Disapprove — never auto-publish blind.",
            },
            {
              t: "3. Publish to Instagram",
              d: "Official Instagram Graph Content Publishing. Optional Instagram automatic Facebook handler via linked Page.",
            },
          ].map((step) => (
            <li key={step.t}>
              <h3 className="text-lg font-semibold text-[var(--ink)]">{step.t}</h3>
              <p className="mt-2 text-sm leading-relaxed text-[var(--ink-muted)]">
                {step.d}
              </p>
            </li>
          ))}
        </ol>
      </section>

      <section className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark max-w-2xl text-3xl text-[var(--brand-deep)] sm:text-4xl">
          Instagram automation India, built for relevance
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          Searching for Instagram promotional help, an automatic Instagram post
          handler, or an Instagram marketing service? {PRODUCT_NAME} focuses on
          quality and consistency — not guaranteed #1 ranking claims.
        </p>
        <ul className="mt-8 grid gap-4 text-sm text-[var(--ink-muted)] sm:grid-cols-2">
          <li className="border-l-2 border-[var(--brand)] pl-4">
            Salons & clinics — customer-face consent aware, HITL approve
          </li>
          <li className="border-l-2 border-[var(--accent)] pl-4">
            Official Meta APIs only — no password collection, no scrapers
          </li>
          <li className="border-l-2 border-[var(--brand)] pl-4">
            Punjab & India-first copy — Hinglish captions that feel natural
          </li>
          <li className="border-l-2 border-[var(--accent)] pl-4">
            Optional Facebook Page cross-post when you connect both
          </li>
        </ul>
        <div className="mt-10">
          <Link href="/blog" className="btn-secondary">
            Read Instagram automation guides
          </Link>
        </div>
      </section>

      <section className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
          Ready for roz-roz posting?
        </h2>
        <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
          Create your business profile, switch Instagram to Professional in-app,
          then connect. {TAGLINE}.
        </p>
        <Link href="/onboarding" className="btn-primary mt-8 inline-flex">
          Begin onboarding
        </Link>
      </section>
    </>
  );
}

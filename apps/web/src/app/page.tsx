import Link from "next/link";
import type { Metadata } from "next";
import { JsonLd } from "@/components/json-ld";
import { PRODUCT_NAME } from "@/lib/brand";
import {
  buildMetadata,
  DEFAULT_DESCRIPTION,
  DEFAULT_TITLE,
  faqPageJsonLd,
  localBusinessJsonLd,
  softwareApplicationJsonLd,
} from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: DEFAULT_TITLE,
  description: DEFAULT_DESCRIPTION,
  path: "/",
});

const FAQS = [
  {
    question: "What is an automatic Instagram post handler?",
    answer:
      "In RozPost it means WhatsApp photos are polished into drafts you Approve before official Instagram Graph publishing — not a password bot that posts blind.",
  },
  {
    question: "What does automatic Instagram handler mean for local shops?",
    answer:
      "Phone-first automation: send media on WhatsApp, review the creative, then publish. Built for Indian salons, clinics, and retail — Instagram automation India without unofficial APIs.",
  },
  {
    question: "Can RozPost act as an Instagram automatic Facebook handler?",
    answer:
      "Yes, optionally. After Professional Instagram and a linked Facebook Page, approved posts can cross-post via official Page permissions — never private APIs.",
  },
  {
    question: "Is RozPost an Instagram marketing service for India?",
    answer:
      "Yes — a productized Instagram marketing service: WhatsApp intake, HITL approve, Instagram (and optional Facebook) out. Plans target ₹299–399/mo after a free pilot band. No guaranteed #1 ranking claims.",
  },
  {
    question: "What is Instagram promotional content in RozPost?",
    answer:
      "Real shop photos turned into promotional posts and simple reels — festival offers, before/after work, product shots — with captions you approve.",
  },
  {
    question: "Also searched as — WhatsApp se Instagram post?",
    answer:
      "Hinglish for the same wedge: WhatsApp se Instagram post / व्हाट्सऐप से इंस्टाग्राम. RozPost is built exactly for that workflow.",
  },
];

export default function HomePage() {
  return (
    <>
      <JsonLd
        data={[
          softwareApplicationJsonLd(),
          localBusinessJsonLd(),
          faqPageJsonLd(FAQS),
        ]}
      />

      <section className="hero-wash relative min-h-[88vh] text-white">
        <div className="site-shell flex min-h-[88vh] flex-col justify-end gap-6 pb-16 pt-24 sm:justify-center sm:pb-24">
          <p className="brand-mark fade-up text-4xl tracking-tight sm:text-6xl md:text-7xl">
            {PRODUCT_NAME}
          </p>
          <h1 className="fade-up-delay max-w-3xl text-2xl font-semibold leading-snug tracking-tight sm:text-4xl">
            WhatsApp photos in. Instagram posts out.
          </h1>
          <p className="fade-up-delay-2 max-w-xl text-base text-white/85 sm:text-lg">
            Instagram marketing for local businesses in India — send shop photos
            on WhatsApp, approve AI drafts, publish daily. Official Meta APIs
            only.
          </p>
          <div className="fade-up-delay-2 flex flex-wrap gap-3">
            <Link
              href="/onboarding"
              className="inline-flex items-center justify-center rounded-md bg-[var(--accent-soft)] px-5 py-3 text-sm font-semibold text-[var(--brand-deep)] transition hover:bg-white"
            >
              Start free onboarding
            </Link>
            <Link
              href="/blog"
              className="inline-flex items-center justify-center rounded-md border border-white/35 bg-white/10 px-5 py-3 text-sm font-semibold text-white backdrop-blur transition hover:bg-white/20"
            >
              Read guides
            </Link>
          </div>
        </div>
      </section>

      <section id="how-it-works" className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark max-w-2xl text-3xl text-[var(--brand-deep)] sm:text-4xl">
          How {PRODUCT_NAME} works
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          WhatsApp to Instagram for owners who do not live in Meta Business
          Suite — auto post with your Approve tap.
        </p>
        <ol className="mt-10 grid gap-8 sm:grid-cols-3">
          {[
            {
              t: "1. Send on WhatsApp",
              d: "Photo or short video from the floor, optional style notes in the caption.",
            },
            {
              t: "2. Approve the draft",
              d: "AI polish + captions. Approve or Disapprove — never blind auto-publish.",
            },
            {
              t: "3. Publish to Instagram",
              d: "Official Content Publishing. Optional Facebook Page cross-post when connected.",
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
          Built for Indian local businesses
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          Vertical Instagram marketing for salons, clinics, and shops — Punjab
          and across India.
        </p>
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {["Salons", "Clinics", "Furniture & retail", "Tuition & institutes"].map(
            (v) => (
              <li key={v}>
                <h3 className="text-lg font-semibold">{v}</h3>
                <p className="mt-2 text-sm text-[var(--ink-muted)]">
                  Real work photos → approved Instagram promotional posts.
                </p>
              </li>
            ),
          )}
        </ul>
      </section>

      <section className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
          Instagram + optional Facebook
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          Publish to Instagram first. When a Facebook Page is linked, cross-post
          safely — the compliant reading of an Instagram automatic Facebook
          handler.
        </p>
      </section>

      <section className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
          Official Meta APIs only
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          WhatsApp Cloud API + Instagram Graph Content Publishing. No password
          collection, no scrapers, no private IG APIs.{" "}
          <Link href="/connect" className="font-semibold text-[var(--brand)]">
            Connect Instagram
          </Link>{" "}
          after Professional setup in-app.
        </p>
      </section>

      <section className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
          Pricing
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
          Free pilot for early shops, then an Instagram marketing service plan
          around ₹299–399/client/month. Quality and consistency — not guaranteed
          ranking.
        </p>
        <Link href="/onboarding" className="btn-primary mt-8 inline-flex">
          Start onboarding
        </Link>
      </section>

      <section className="site-shell section-rule py-16 sm:py-20">
        <h2 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
          FAQ
        </h2>
        <div className="mt-8 space-y-6">
          {FAQS.map((f) => (
            <div key={f.question}>
              <h3 className="text-lg font-semibold text-[var(--ink)]">{f.question}</h3>
              <p className="mt-2 text-sm leading-relaxed text-[var(--ink-muted)]">
                {f.question.includes("WhatsApp se") ? (
                  <>
                    <span lang="hi" className="lang-hi">
                      व्हाट्सऐप से इंस्टाग्राम
                    </span>
                    {" — "}
                    {f.answer}
                  </>
                ) : (
                  f.answer
                )}
              </p>
            </div>
          ))}
        </div>
        <p className="mt-8 text-sm text-[var(--ink-muted)]">
          Punjabi owners often say{" "}
          <span lang="pa" className="lang-pa">
            ਵਟਸਐਪ ਤੋਂ ਇੰਸਟਾਗ੍ਰਾਮ
          </span>{" "}
          — same product: WhatsApp photos → Instagram, every day.
        </p>
      </section>
    </>
  );
}

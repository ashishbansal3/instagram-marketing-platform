import type { Metadata } from "next";
import Link from "next/link";
import { PRODUCT_NAME } from "@/lib/brand";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: `Dashboard — ${PRODUCT_NAME}`,
  description: `${PRODUCT_NAME} analytics dashboard stub: visits, reach, engagement placeholders for the demo.`,
  path: "/dashboard",
  noIndex: true,
});

const PLACEHOLDERS = [
  { label: "Profile visits", value: "—", hint: "IG Insights sync (v1)" },
  { label: "Reach (7d)", value: "—", hint: "Rolling window" },
  { label: "Engagement", value: "—", hint: "Likes + comments + saves" },
  { label: "Posts published", value: "—", hint: "Quota-aware count" },
  { label: "Pending approvals", value: "—", hint: "WhatsApp HITL queue" },
  { label: "Website visits", value: "—", hint: "First-party beacon" },
];

export default function DashboardPage() {
  return (
    <div className="site-shell py-12 sm:py-16">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
            Dashboard
          </h1>
          <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
            Analytics placeholders for the {PRODUCT_NAME} demo. Live Insights
            sync ships in v1; this stub links the surface for tomorrow&apos;s
            walkthrough.
          </p>
        </div>
        <Link href="/connect" className="btn-secondary">
          Reconnect Instagram
        </Link>
      </div>

      <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {PLACEHOLDERS.map((m) => (
          <div
            key={m.label}
            className="rounded-lg border border-[var(--line)] bg-white/70 p-5 backdrop-blur"
          >
            <p className="text-sm text-[var(--ink-muted)]">{m.label}</p>
            <p className="mt-2 font-display text-3xl font-semibold tracking-tight text-[var(--ink)]">
              {m.value}
            </p>
            <p className="mt-2 text-xs text-[var(--ink-muted)]">{m.hint}</p>
          </div>
        ))}
      </div>

      <section className="mt-12 section-rule pt-10">
        <h2 className="text-xl font-semibold">Quick links</h2>
        <ul className="mt-4 flex flex-wrap gap-3 text-sm">
          <li>
            <Link className="btn-secondary" href="/onboarding">
              Onboarding
            </Link>
          </li>
          <li>
            <Link className="btn-secondary" href="/blog">
              SEO guides
            </Link>
          </li>
          <li>
            <a className="btn-secondary" href="/api/dashboard/summary">
              GET /api/dashboard/summary
            </a>
          </li>
        </ul>
      </section>
    </div>
  );
}

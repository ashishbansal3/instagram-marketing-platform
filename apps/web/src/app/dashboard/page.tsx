import type { Metadata } from "next";
import Link from "next/link";
import { PRODUCT_NAME } from "@/lib/brand";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: `Dashboard | ${PRODUCT_NAME}`,
  description: `${PRODUCT_NAME} dashboard — posts, approvals, and insights.`,
  path: "/dashboard",
  noIndex: true,
});

const PLACEHOLDERS = [
  { label: "Profile visits", value: "—", hint: "IG Insights sync" },
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
            Your posts & approvals
          </h1>
          <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
            Send photos on WhatsApp to fill this queue. Insights appear after
            Instagram is connected and sync runs.
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
    </div>
  );
}

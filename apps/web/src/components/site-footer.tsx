import Link from "next/link";
import { PRODUCT_NAME } from "@/lib/brand";

export function SiteFooter() {
  return (
    <footer className="section-rule mt-20">
      <div className="site-shell flex flex-col gap-4 py-10 text-sm text-[var(--ink-muted)] sm:flex-row sm:items-start sm:justify-between">
        <div className="max-w-md space-y-2">
          <p>
            <span className="brand-mark text-[var(--ink)]">{PRODUCT_NAME}</span>
            {" — "}
            WhatsApp to Instagram for Indian local businesses. Official Meta
            APIs only.
          </p>
          <p>
            Service area: Punjab (Ludhiana, Chandigarh, Amritsar, Jalandhar) and
            shops across India.{" "}
            <Link href="/onboarding" className="font-semibold text-[var(--brand)]">
              Contact via onboarding
            </Link>
            .
          </p>
        </div>
        <div className="flex flex-wrap gap-4">
          <Link href="/blog" className="hover:text-[var(--ink)]">
            Blog
          </Link>
          <Link href="/onboarding" className="hover:text-[var(--ink)]">
            Onboarding
          </Link>
          <Link href="/connect" className="hover:text-[var(--ink)]">
            Connect
          </Link>
          <Link href="/dashboard" className="hover:text-[var(--ink)]">
            Dashboard
          </Link>
        </div>
      </div>
    </footer>
  );
}

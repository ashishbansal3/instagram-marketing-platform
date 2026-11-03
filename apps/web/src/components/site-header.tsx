import Link from "next/link";
import { PRODUCT_NAME } from "@/lib/brand";

const links = [
  { href: "/#how-it-works", label: "How it works" },
  { href: "/blog", label: "Guides" },
  { href: "/onboarding", label: "Start" },
  { href: "/dashboard", label: "Dashboard" },
];

export function SiteHeader() {
  return (
    <header className="site-shell flex items-center justify-between gap-4 py-5">
      <Link href="/" className="brand-mark text-xl text-[var(--brand-deep)] sm:text-2xl">
        {PRODUCT_NAME}
      </Link>
      <nav className="flex flex-wrap items-center justify-end gap-x-4 gap-y-2 text-sm font-medium text-[var(--ink-muted)]">
        {links.map((l) => (
          <Link key={l.href} href={l.href} className="hover:text-[var(--ink)]">
            {l.label}
          </Link>
        ))}
      </nav>
    </header>
  );
}

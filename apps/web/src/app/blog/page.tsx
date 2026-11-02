import type { Metadata } from "next";
import Link from "next/link";
import { BLOG_POSTS } from "@/lib/blog";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: "Instagram Marketing Guides for Indian Local Businesses | RozPost Blog",
  description:
    "Guides on WhatsApp to Instagram, salon and clinic marketing, Professional setup, and safe Facebook cross-post for Indian shops.",
  path: "/blog",
});

export default function BlogIndexPage() {
  return (
    <div className="site-shell py-12 sm:py-16">
      <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
        Guides: Instagram marketing for shops that run on WhatsApp
      </h1>
      <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
        Practical Instagram marketing service playbooks for Indian local
        businesses — then{" "}
        <Link href="/onboarding" className="font-semibold text-[var(--brand)]">
          start onboarding
        </Link>
        .
      </p>
      <ul className="mt-10 space-y-8">
        {BLOG_POSTS.map((post) => (
          <li key={post.slug} className="section-rule pt-8 first:border-0 first:pt-0">
            <p className="text-xs uppercase tracking-wide text-[var(--ink-muted)]">
              {post.publishedAt} · {post.readingMinutes} min
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight">
              <Link href={`/blog/${post.slug}`} className="hover:text-[var(--brand)]">
                {post.title}
              </Link>
            </h2>
            <p className="mt-2 max-w-2xl text-[var(--ink-muted)]">{post.description}</p>
            <Link
              href={`/blog/${post.slug}`}
              className="mt-3 inline-block text-sm font-semibold text-[var(--brand)]"
            >
              Read {post.primaryKeyword} guide →
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

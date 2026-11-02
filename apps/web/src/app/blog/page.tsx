import type { Metadata } from "next";
import Link from "next/link";
import { BLOG_POSTS } from "@/lib/blog";
import { PRODUCT_NAME } from "@/lib/brand";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: `Guides — Instagram automation for Indian businesses`,
  description: `Practical ${PRODUCT_NAME} guides on Instagram automation India for salons, clinics, and local shops — WhatsApp workflows and automatic Instagram handlers.`,
  path: "/blog",
});

export default function BlogIndexPage() {
  return (
    <div className="site-shell py-12 sm:py-16">
      <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
        Instagram automation guides
      </h1>
      <p className="mt-3 max-w-2xl text-[var(--ink-muted)]">
        SEO articles for Indian local businesses exploring Instagram promotional
        workflows with {PRODUCT_NAME}.
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
              Read article →
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

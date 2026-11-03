import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { BLOG_POSTS, getPost } from "@/lib/blog";
import { PRODUCT_NAME } from "@/lib/brand";
import { buildMetadata } from "@/lib/seo";
import { JsonLd } from "@/components/json-ld";

export function generateStaticParams() {
  return BLOG_POSTS.map((p) => ({ slug: p.slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const post = getPost(slug);
  if (!post) return {};
  return buildMetadata({
    title: `${post.title} | RozPost`,
    description: post.description,
    path: `/blog/${post.slug}`,
  });
}

export default async function BlogPostPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const post = getPost(slug);
  if (!post) notFound();

  const siblings = BLOG_POSTS.filter((p) => p.slug !== post.slug).slice(0, 2);

  const articleLd = {
    "@context": "https://schema.org",
    "@type": "BlogPosting",
    headline: post.title,
    description: post.description,
    datePublished: post.publishedAt,
    dateModified: post.updatedAt,
    author: { "@type": "Organization", name: PRODUCT_NAME },
    publisher: { "@type": "Organization", name: PRODUCT_NAME },
    keywords: post.keywords.join(", "),
    mainEntityOfPage: `${process.env.NEXT_PUBLIC_SITE_URL ?? "https://rozpost.com"}/blog/${post.slug}`,
  };

  return (
    <article className="site-shell py-12 sm:py-16">
      <JsonLd data={articleLd} />
      <p className="text-xs uppercase tracking-wide text-[var(--ink-muted)]">
        <Link href="/blog" className="hover:text-[var(--ink)]">
          Guides
        </Link>{" "}
        · {post.publishedAt} · {post.readingMinutes} min
      </p>
      <h1 className="brand-mark mt-3 max-w-3xl text-3xl text-[var(--brand-deep)] sm:text-5xl">
        {post.title}
      </h1>
      <p className="mt-4 max-w-2xl text-lg text-[var(--ink-muted)]">{post.description}</p>
      <div className="mt-10 max-w-2xl space-y-5">
        {post.body.map((para) => (
          <p key={para.slice(0, 40)} className="leading-relaxed text-[var(--ink-muted)]">
            {para}
          </p>
        ))}
      </div>

      <p className="mt-12 max-w-xl rounded-md border border-[var(--line)] bg-white/70 p-5 text-sm text-[var(--ink-muted)]">
        Ready for WhatsApp to Instagram?{" "}
        <Link href="/onboarding" className="font-semibold text-[var(--brand)]">
          Start onboarding
        </Link>{" "}
        or{" "}
        <Link href="/connect" className="font-semibold text-[var(--brand)]">
          connect Instagram
        </Link>
        .
      </p>

      <aside className="mt-10">
        <h2 className="text-lg font-semibold">Related guides</h2>
        <ul className="mt-3 space-y-2 text-sm">
          {siblings.map((s) => (
            <li key={s.slug}>
              <Link href={`/blog/${s.slug}`} className="text-[var(--brand)]">
                {s.title}
              </Link>
            </li>
          ))}
        </ul>
      </aside>
    </article>
  );
}

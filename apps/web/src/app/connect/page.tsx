import type { Metadata } from "next";
import Link from "next/link";
import { OAuthConnect } from "@/components/oauth-connect";
import { WebsiteSetupStatus } from "@/components/website-setup-status";
import { JsonLd } from "@/components/json-ld";
import { buildMetadata, howToConnectJsonLd } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: "Connect Instagram (and Facebook) | RozPost",
  description:
    "Switch to Instagram Professional in the official app, then connect securely. Optional Facebook Page cross-posting.",
  path: "/connect",
});

export default async function ConnectPage({
  searchParams,
}: {
  searchParams: Promise<{ onboardingId?: string }>;
}) {
  const { onboardingId } = await searchParams;

  return (
    <div className="site-shell py-12 sm:py-16">
      <JsonLd data={howToConnectJsonLd()} />
      <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
        Connect Instagram the compliant way
      </h1>
      <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
        Switch to Professional in the Instagram app, then authorize RozPost.
        Optional Facebook Page enables safe cross-post — the honest version of
        an Instagram automatic Facebook handler.{" "}
        <Link href="/onboarding" className="font-semibold text-[var(--brand)]">
          Back to onboarding
        </Link>
        .
      </p>

      <section className="mt-10">
        <h2 className="text-xl font-semibold">
          Switch to Professional (in the Instagram app)
        </h2>
        <p className="mt-2 text-sm text-[var(--ink-muted)]">
          See our{" "}
          <Link
            href="/blog/instagram-professional-account-setup-india"
            className="text-[var(--brand)]"
          >
            Professional setup guide
          </Link>{" "}
          and Meta&apos;s{" "}
          <a
            className="underline underline-offset-2"
            href="https://help.instagram.com/502981923235522"
            target="_blank"
            rel="noreferrer"
          >
            official Help Center article
          </a>
          .
        </p>
      </section>

      <WebsiteSetupStatus onboardingId={onboardingId} />

      <section className="mt-10">
        <h2 className="text-xl font-semibold">Connect with Instagram Login</h2>
        <div className="mt-6">
          <OAuthConnect onboardingId={onboardingId} />
        </div>
      </section>

      <section className="mt-12 section-rule pt-10">
        <h2 className="text-xl font-semibold">Optional Facebook Page</h2>
        <h3 className="mt-4 text-base font-semibold">Cross-post FAQ</h3>
        <p className="mt-2 text-sm text-[var(--ink-muted)]">
          After Page linking, approved Instagram posts can publish to Facebook
          via official permissions — not unofficial “automatic Facebook handler”
          bots. Read{" "}
          <Link
            href="/blog/instagram-facebook-cross-post-safely"
            className="text-[var(--brand)]"
          >
            safe IG+FB cross-post
          </Link>
          .
        </p>
      </section>
    </div>
  );
}

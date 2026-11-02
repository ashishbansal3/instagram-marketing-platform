import type { Metadata } from "next";
import { OAuthConnect } from "@/components/oauth-connect";
import { PRODUCT_NAME } from "@/lib/brand";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: `Connect Instagram — ${PRODUCT_NAME}`,
  description: `Connect your Professional Instagram account to ${PRODUCT_NAME}. Demo mode stores fake tokens; production uses Instagram Business Login.`,
  path: "/connect",
  noIndex: true,
});

export default async function ConnectPage({
  searchParams,
}: {
  searchParams: Promise<{ onboardingId?: string }>;
}) {
  const { onboardingId } = await searchParams;

  return (
    <div className="site-shell py-12 sm:py-16">
      <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
        Connect Instagram
      </h1>
      <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
        {PRODUCT_NAME} uses official Instagram Login after you convert to a
        Professional account in the Instagram app. We never ask for your
        password.
      </p>
      <div className="mt-10">
        <OAuthConnect onboardingId={onboardingId} />
      </div>
    </div>
  );
}

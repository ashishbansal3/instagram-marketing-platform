import type { Metadata } from "next";
import Link from "next/link";
import { OnboardingForm } from "@/components/onboarding-form";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: "Start Instagram Marketing for Your Shop | RozPost Onboarding",
  description:
    "Add your phone and business details. We’ll guide Professional Instagram setup, then you send photos on WhatsApp.",
  path: "/onboarding",
});

export default function OnboardingPage() {
  return (
    <div className="site-shell py-12 sm:py-16">
      <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
        Get your shop on Instagram autopilot
      </h1>
      <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
        Start your Instagram marketing service setup. Next you&apos;ll{" "}
        <Link href="/connect" className="font-semibold text-[var(--brand)]">
          connect Instagram Professional
        </Link>{" "}
        and send photos on WhatsApp.
      </p>

      <div className="mt-10 space-y-8">
        <section>
          <h2 className="text-xl font-semibold">Business details</h2>
          <p className="mt-1 text-sm text-[var(--ink-muted)]">
            Phone (WhatsApp), name, category, city, bio, optional first photo.
          </p>
          <div className="mt-6">
            <OnboardingForm />
          </div>
        </section>
        <section>
          <h2 className="text-xl font-semibold">What happens next</h2>
          <h3 className="mt-4 text-base font-semibold">Why we need WhatsApp</h3>
          <p className="mt-1 text-sm text-[var(--ink-muted)]">
            Intake is 1:1 WhatsApp Cloud API — the same place you already send
            shop photos.
          </p>
          <h3 className="mt-4 text-base font-semibold">Professional setup</h3>
          <p className="mt-1 text-sm text-[var(--ink-muted)]">
            After this form, follow{" "}
            <Link href="/blog/instagram-professional-account-setup-india" className="text-[var(--brand)]">
              Instagram Professional setup
            </Link>{" "}
            then Connect.
          </p>
        </section>
      </div>
    </div>
  );
}

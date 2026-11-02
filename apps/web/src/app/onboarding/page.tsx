import type { Metadata } from "next";
import { OnboardingForm } from "@/components/onboarding-form";
import { PRODUCT_NAME } from "@/lib/brand";
import { buildMetadata } from "@/lib/seo";

export const metadata: Metadata = buildMetadata({
  title: `Onboarding — Instagram marketing service setup | ${PRODUCT_NAME}`,
  description: `Start ${PRODUCT_NAME} onboarding for Instagram automation India: phone, business name, category, city, bio. Automatic Instagram post handler for salons and clinics — then Connect Instagram.`,
  path: "/onboarding",
});

export default function OnboardingPage() {
  return (
    <div className="site-shell py-12 sm:py-16">
      <h1 className="brand-mark text-3xl text-[var(--brand-deep)] sm:text-4xl">
        Start with {PRODUCT_NAME}
      </h1>
      <p className="mt-3 max-w-xl text-[var(--ink-muted)]">
        Tell us about your business. Next you&apos;ll switch Instagram to
        Professional in the official app and connect. WhatsApp photos →
        Instagram, every day.
      </p>
      <div className="mt-10">
        <OnboardingForm />
      </div>
    </div>
  );
}

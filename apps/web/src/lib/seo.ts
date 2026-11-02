import type { Metadata } from "next";
import { DOMAIN_PRIMARY, PRODUCT_NAME, SITE_URL, TAGLINE } from "./brand";

/** Seed + remapped keywords from docs/rozpost-seo-keyword-research.md */
export const SEO_KEYWORDS = [
  "WhatsApp to Instagram",
  "WhatsApp se Instagram post",
  "Instagram marketing for local businesses",
  "Instagram marketing service",
  "Instagram automation India",
  "Instagram auto post",
  "Instagram promotional",
  "automatic Instagram handler",
  "automatic Instagram post handler",
  "Instagram automatic Facebook handler",
  "Instagram marketing for salons",
  "Instagram marketing for clinics",
  "Instagram Facebook cross posting",
  "Instagram Professional account setup",
  "RozPost",
] as const;

export const DEFAULT_DESCRIPTION =
  "Send shop photos on WhatsApp. Approve AI drafts. Publish to Instagram daily — built for Indian salons, clinics, and local shops.";

export const DEFAULT_TITLE =
  "WhatsApp to Instagram for Local Businesses | RozPost";

export function buildMetadata(opts: {
  title: string;
  description?: string;
  path?: string;
  ogImage?: string;
  noIndex?: boolean;
}): Metadata {
  const description = opts.description ?? DEFAULT_DESCRIPTION;
  const path = opts.path ?? "/";
  const url = `${SITE_URL}${path === "/" ? "/" : path}`;
  const ogImage = opts.ogImage ?? `${SITE_URL}/og.svg`;
  const fullTitle = opts.title.includes(PRODUCT_NAME)
    ? opts.title
    : `${opts.title} | ${PRODUCT_NAME}`;

  return {
    metadataBase: new URL(SITE_URL),
    title: fullTitle,
    description,
    keywords: [...SEO_KEYWORDS],
    alternates: { canonical: url },
    robots: opts.noIndex
      ? { index: false, follow: false }
      : { index: true, follow: true },
    openGraph: {
      type: "website",
      locale: "en_IN",
      url,
      siteName: PRODUCT_NAME,
      title: fullTitle,
      description,
      images: [{ url: ogImage, width: 1200, height: 630, alt: PRODUCT_NAME }],
    },
    twitter: {
      card: "summary_large_image",
      title: fullTitle,
      description,
      images: [ogImage],
    },
  };
}

export function softwareApplicationJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: PRODUCT_NAME,
    applicationCategory: "BusinessApplication",
    operatingSystem: "Web",
    url: SITE_URL,
    description: DEFAULT_DESCRIPTION,
    slogan: TAGLINE,
    offers: {
      "@type": "Offer",
      priceCurrency: "INR",
      price: "0",
      description:
        "Free pilot for early local businesses; paid plans from ₹299/mo",
    },
    featureList: [
      "WhatsApp photo intake",
      "AI-polished Instagram posts and reels",
      "Human-in-the-loop approve before publish",
      "Optional Facebook Page cross-post",
      "Official Meta WhatsApp Cloud + Instagram Graph APIs",
    ],
    areaServed: { "@type": "Country", name: "India" },
    inLanguage: ["en-IN", "hi-IN", "pa-IN"],
  };
}

export function localBusinessJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "LocalBusiness",
    name: PRODUCT_NAME,
    description: `${PRODUCT_NAME} — Instagram marketing service for Indian local businesses. ${TAGLINE}. Service area: Punjab, India.`,
    url: SITE_URL,
    sameAs: [`https://${DOMAIN_PRIMARY}`],
    areaServed: [
      { "@type": "State", name: "Punjab" },
      { "@type": "City", name: "Ludhiana" },
      { "@type": "City", name: "Chandigarh" },
      { "@type": "City", name: "Amritsar" },
      { "@type": "Country", name: "India" },
    ],
    priceRange: "₹₹",
    knowsLanguage: ["en", "hi", "pa"],
  };
}

export function faqPageJsonLd(
  faqs: { question: string; answer: string }[],
) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: faqs.map((f) => ({
      "@type": "Question",
      name: f.question,
      acceptedAnswer: { "@type": "Answer", text: f.answer },
    })),
  };
}

export function howToConnectJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "HowTo",
    name: "Connect Instagram Professional to RozPost",
    description:
      "Switch to Instagram Professional in the official app, then connect with Instagram Login.",
    step: [
      {
        "@type": "HowToStep",
        name: "Open Instagram settings",
        text: "In the Instagram app go to profile → menu → Settings and privacy → Account type and tools.",
      },
      {
        "@type": "HowToStep",
        name: "Switch to professional account",
        text: "Tap Switch to professional account, pick a category, choose Business or Creator.",
      },
      {
        "@type": "HowToStep",
        name: "Connect with RozPost",
        text: "Return to RozPost and use Connect Instagram (Instagram Business Login). Optional: link a Facebook Page for cross-post.",
      },
    ],
  };
}

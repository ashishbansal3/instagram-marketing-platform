import type { Metadata } from "next";
import { DOMAIN_PRIMARY, PRODUCT_NAME, SITE_URL, TAGLINE } from "./brand";

/** Primary SEO phrases for Indian local-business Instagram automation. */
export const SEO_KEYWORDS = [
  "Instagram promotional",
  "automatic Instagram handler",
  "automatic Instagram post handler",
  "Instagram automatic Facebook handler",
  "Instagram automation India",
  "Instagram marketing service",
  "Instagram marketing for salons",
  "clinic Instagram automation",
  "WhatsApp to Instagram posting",
  "Instagram reels for local business",
  "Punjab Instagram marketing",
  "salon Instagram posts India",
  "Instagram automatic posting service",
  "AI Instagram handler India",
  "RozPost",
] as const;

export const DEFAULT_DESCRIPTION = `${PRODUCT_NAME} — ${TAGLINE}. Instagram marketing service for Indian local businesses (salons, clinics, shops). Send photos on WhatsApp, approve polished drafts, publish with an automatic Instagram post handler. Optional Facebook cross-post. Official Meta APIs only.`;

export function buildMetadata(opts: {
  title: string;
  description?: string;
  path?: string;
  ogImage?: string;
  noIndex?: boolean;
}): Metadata {
  const description = opts.description ?? DEFAULT_DESCRIPTION;
  const path = opts.path ?? "/";
  const url = `${SITE_URL}${path}`;
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
    areaServed: {
      "@type": "Country",
      name: "India",
    },
    inLanguage: ["en-IN", "hi-IN", "pa-IN"],
  };
}

export function localBusinessJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "LocalBusiness",
    name: PRODUCT_NAME,
    description: `${PRODUCT_NAME} — Instagram automation India. Marketing service helping salons, clinics, and shops publish promotional posts via WhatsApp. ${TAGLINE}.`,
    url: SITE_URL,
    sameAs: [`https://${DOMAIN_PRIMARY}`],
    areaServed: [
      { "@type": "State", name: "Punjab" },
      { "@type": "Country", name: "India" },
    ],
    priceRange: "₹₹",
    knowsLanguage: ["en", "hi", "pa"],
  };
}

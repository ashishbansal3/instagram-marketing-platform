export const PRODUCT_NAME = "RozPost";

export const TAGLINE = "WhatsApp photos → Instagram, every day";

export const DOMAIN_PRIMARY = "rozpost.com";

export const SITE_URL =
  process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/$/, "") ||
  `https://${DOMAIN_PRIMARY}`;

export const SUPPORT_LOCALE = "en-IN";

export const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE !== "false";

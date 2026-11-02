import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  const base = process.env.NEXT_PUBLIC_SITE_URL || "https://rozpost.in";
  return {
    rules: [
      { userAgent: "*", allow: "/", disallow: ["/dashboard", "/d/"] }
    ],
    sitemap: `${base}/sitemap.xml`
  };
}

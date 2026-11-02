import type { Metadata } from "next";
import {
  Bricolage_Grotesque,
  Figtree,
  Noto_Sans_Devanagari,
  Noto_Sans_Gurmukhi,
} from "next/font/google";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { PRODUCT_NAME, TAGLINE } from "@/lib/brand";
import { buildMetadata, DEFAULT_DESCRIPTION } from "@/lib/seo";
import "./globals.css";

const display = Bricolage_Grotesque({
  subsets: ["latin"],
  variable: "--font-display",
  display: "swap",
});

const body = Figtree({
  subsets: ["latin"],
  variable: "--font-body",
  display: "swap",
});

/** Hindi (Devanagari) + Punjabi (Gurmukhi) for India-first copy / captions UI. */
const devanagari = Noto_Sans_Devanagari({
  subsets: ["devanagari"],
  variable: "--font-devanagari",
  display: "swap",
  weight: ["400", "600", "700"],
});

const gurmukhi = Noto_Sans_Gurmukhi({
  subsets: ["gurmukhi"],
  variable: "--font-gurmukhi",
  display: "swap",
  weight: ["400", "600", "700"],
});

export const metadata: Metadata = {
  ...buildMetadata({
    title: `${PRODUCT_NAME} — ${TAGLINE}`,
    description: DEFAULT_DESCRIPTION,
    path: "/",
  }),
  title: {
    default: `${PRODUCT_NAME} — ${TAGLINE}`,
    template: `%s | ${PRODUCT_NAME}`,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en-IN">
      <body
        className={`${display.variable} ${body.variable} ${devanagari.variable} ${gurmukhi.variable} antialiased`}
      >
        <SiteHeader />
        <main>{children}</main>
        <SiteFooter />
      </body>
    </html>
  );
}

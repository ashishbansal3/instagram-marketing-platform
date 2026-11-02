import type { Metadata } from "next";
import { Bricolage_Grotesque, Figtree } from "next/font/google";
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
      <body className={`${display.variable} ${body.variable} antialiased`}>
        <SiteHeader />
        <main>{children}</main>
        <SiteFooter />
      </body>
    </html>
  );
}

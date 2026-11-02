import type { Metadata } from "next";
import { Manrope, Fraunces } from "next/font/google";
import "./globals.css";

const manrope = Manrope({ subsets: ["latin"], variable: "--font-sans" });
const fraunces = Fraunces({ subsets: ["latin"], variable: "--font-display" });

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://rozpost.in"),
  title: {
    default: "RozPost — WhatsApp to Instagram for local businesses",
    template: "%s · RozPost"
  },
  description:
    "Send photos on WhatsApp. RozPost polishes, asks Approve/Disapprove, then publishes to Instagram. Official Meta APIs only.",
  openGraph: {
    title: "RozPost",
    description: "WhatsApp photos in. Instagram posts out.",
    type: "website",
    locale: "en_IN"
  },
  robots: { index: true, follow: true }
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={`${manrope.variable} ${fraunces.variable}`}>{children}</body>
    </html>
  );
}

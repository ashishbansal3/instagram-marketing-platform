import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "RozPost — WhatsApp to Instagram for local businesses",
  description: "Send photos on WhatsApp. Approve. Publish to Instagram."
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

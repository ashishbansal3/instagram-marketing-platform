import type { NextConfig } from "next";

/**
 * When SPRING_API_URL is set, /api/proxy/* forwards to Spring.
 * Local App Router handlers (onboarding, oauth demo, dashboard summary)
 * take precedence for paths we implement as BFF/mock.
 */
const spring = process.env.SPRING_API_URL?.replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    if (!spring) return [];
    return [
      {
        source: "/api/proxy/:path*",
        destination: `${spring}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;

import type { NextConfig } from "next";

/**
 * When SPRING_API_URL is set, /api/v1/proxy/* forwards to Spring /api/v1/*.
 * App Router handlers under /api/v1/* take precedence for paths we implement as BFF.
 */
const spring = process.env.SPRING_API_URL?.replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    if (!spring) return [];
    return [
      {
        source: "/api/v1/proxy/:path*",
        destination: `${spring}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;

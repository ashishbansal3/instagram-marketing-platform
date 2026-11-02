import { NextResponse } from "next/server";

const SPRING_API_URL = process.env.SPRING_API_URL?.replace(/\/$/, "");

export async function GET() {
  if (SPRING_API_URL) {
    try {
      const upstream = await fetch(`${SPRING_API_URL}/api/dashboard/summary`);
      const text = await upstream.text();
      return new NextResponse(text, {
        status: upstream.status,
        headers: {
          "content-type":
            upstream.headers.get("content-type") || "application/json",
        },
      });
    } catch {
      /* mock */
    }
  }

  return NextResponse.json({
    demo: true,
    metrics: {
      profileVisits: null,
      reach7d: null,
      engagement: null,
      postsPublished: null,
      pendingApprovals: null,
      websiteVisits: null,
    },
    message:
      "Analytics placeholders — wire IG Insights sync in v1. Dashboard UI is ready.",
  });
}

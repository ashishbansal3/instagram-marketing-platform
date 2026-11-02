import { NextRequest, NextResponse } from "next/server";
import { env } from "@/config/env";
import { getCorrelationId, CORRELATION_HEADER } from "@/lib/correlation";
import { statusFor, toErrorBody } from "@/lib/errors";

export async function GET(req: NextRequest) {
  const correlationId = getCorrelationId(req);
  try {
    if (env.springApiUrl) {
      try {
        const upstream = await fetch(
          `${env.springApiUrl}/api/v1/dashboard/summary`,
          { headers: { [CORRELATION_HEADER]: correlationId } },
        );
        const text = await upstream.text();
        return new NextResponse(text, {
          status: upstream.status,
          headers: {
            "content-type":
              upstream.headers.get("content-type") || "application/json",
            [CORRELATION_HEADER]: correlationId,
          },
        });
      } catch {
        /* fall through */
      }
    }

    return NextResponse.json(
      {
        metaMode: env.metaMode,
        metrics: {
          profileVisits: null,
          reach7d: null,
          engagement: null,
          postsPublished: null,
          pendingApprovals: null,
          websiteVisits: null,
        },
        message:
          "Insights sync not yet populated — dashboard UI is ready for v1 analytics.",
      },
      { headers: { [CORRELATION_HEADER]: correlationId } },
    );
  } catch (err) {
    return NextResponse.json(toErrorBody(err, correlationId), {
      status: statusFor(err),
      headers: { [CORRELATION_HEADER]: correlationId },
    });
  }
}

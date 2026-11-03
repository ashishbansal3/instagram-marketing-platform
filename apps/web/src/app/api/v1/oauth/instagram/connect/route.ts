import { NextRequest, NextResponse } from "next/server";
import { randomBytes } from "crypto";
import { env } from "@/config/env";
import { markOAuthConnected } from "@/application/onboarding-service";
import { getCorrelationId, CORRELATION_HEADER } from "@/lib/correlation";
import { AppError, statusFor, toErrorBody } from "@/lib/errors";

type ConnectBody = {
  onboardingId?: string;
};

/**
 * META_MODE=live → redirect URL for Instagram Business Login (Spring or configured start URL).
 * META_MODE=mock → mint a non-production session for local smoke tests only.
 */
export async function POST(req: NextRequest) {
  const correlationId = getCorrelationId(req);
  try {
    const body = (await req.json().catch(() => ({}))) as ConnectBody;

    if (!env.isMockMeta) {
      const start =
        env.springApiUrl
          ? `${env.springApiUrl}/api/v1/oauth/instagram/start`
          : env.instagramOAuthStartUrl;
      if (!start) {
        throw new AppError(
          "INTERNAL",
          "Instagram OAuth start URL is not configured",
          500,
        );
      }
      const url = new URL(start, env.siteUrl);
      if (body.onboardingId) {
        url.searchParams.set("onboardingId", body.onboardingId);
      }
      url.searchParams.set("correlationId", correlationId);
      return NextResponse.json(
        { redirectUrl: url.toString(), metaMode: "live" },
        { headers: { [CORRELATION_HEADER]: correlationId } },
      );
    }

    markOAuthConnected(body.onboardingId);
    return NextResponse.json(
      {
        provider: "instagram_login",
        accessToken: `mock_ig_${randomBytes(16).toString("hex")}`,
        igUserId: `1784${Math.floor(Math.random() * 1e10)}`,
        connectedAt: new Date().toISOString(),
        onboardingId: body.onboardingId,
        scopes: [
          "instagram_business_basic",
          "instagram_business_content_publish",
          "instagram_business_manage_insights",
        ],
        metaMode: "mock",
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

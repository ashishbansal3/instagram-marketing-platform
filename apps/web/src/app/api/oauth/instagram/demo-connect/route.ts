import { NextRequest, NextResponse } from "next/server";
import { randomBytes } from "crypto";
import { getOnboarding, saveOnboarding } from "@/lib/mock-store";

export async function POST(req: NextRequest) {
  const body = (await req.json().catch(() => ({}))) as {
    onboardingId?: string;
    demoMode?: boolean;
  };

  const accessToken = `demo_ig_${randomBytes(16).toString("hex")}`;
  const igUserId = `1784${Math.floor(Math.random() * 1e10)}`;
  const connectedAt = new Date().toISOString();

  if (body.onboardingId) {
    const existing = getOnboarding(body.onboardingId);
    if (existing) {
      saveOnboarding({ ...existing, status: "oauth_connected" });
    }
  }

  return NextResponse.json({
    provider: "instagram_login",
    accessToken,
    igUserId,
    connectedAt,
    onboardingId: body.onboardingId,
    scopes: [
      "instagram_business_basic",
      "instagram_business_content_publish",
      "instagram_business_manage_insights",
    ],
    demo: true,
  });
}

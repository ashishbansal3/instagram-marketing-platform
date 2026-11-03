import { randomUUID } from "crypto";
import type {
  CreateOnboardingInput,
  OnboardingRecord,
} from "@/domain/onboarding";
import { AppError } from "@/lib/errors";
import {
  getOnboarding,
  saveOnboarding,
} from "@/infrastructure/onboarding-store";
import { env } from "@/config/env";

function validate(input: CreateOnboardingInput): void {
  const missing = (
    ["phone", "businessName", "category", "city", "bio"] as const
  ).filter((k) => !input[k]?.trim());
  if (missing.length > 0) {
    throw new AppError(
      "VALIDATION_ERROR",
      "Required onboarding fields missing",
      400,
      { missing },
    );
  }
}

async function forwardToSpring(
  contentType: string,
  body: ArrayBuffer,
): Promise<Response> {
  const upstream = await fetch(`${env.springApiUrl}/api/v1/onboarding`, {
    method: "POST",
    headers: contentType ? { "content-type": contentType } : undefined,
    body,
  });
  return upstream;
}

export async function createOnboardingFromFormData(
  form: FormData,
  rawBody?: { contentType: string; body: ArrayBuffer },
): Promise<{ record: OnboardingRecord; source: "spring" | "local_fallback" }> {
  if (env.springApiUrl && rawBody) {
    try {
      const upstream = await forwardToSpring(rawBody.contentType, rawBody.body);
      if (upstream.ok) {
        const data = (await upstream.json()) as { id: string } & Partial<OnboardingRecord>;
        const record: OnboardingRecord = {
          id: data.id,
          phone: String(data.phone ?? form.get("phone") ?? ""),
          businessName: String(data.businessName ?? form.get("businessName") ?? ""),
          category: String(data.category ?? form.get("category") ?? ""),
          city: String(data.city ?? form.get("city") ?? ""),
          bio: String(data.bio ?? form.get("bio") ?? ""),
          photoName: data.photoName ?? null,
          status: (data.status as OnboardingRecord["status"]) ?? "profile_collected",
          createdAt: data.createdAt ?? new Date().toISOString(),
        };
        return { record, source: "spring" };
      }
    } catch {
      // Graceful degradation to local fallback when API unreachable in non-live setups.
      if (env.metaMode === "live") {
        throw new AppError(
          "UPSTREAM_UNAVAILABLE",
          "Onboarding API unavailable",
          502,
        );
      }
    }
  }

  const photo = form.get("photo");
  const photoName =
    photo && typeof photo === "object" && "name" in photo && "size" in photo && photo.size > 0
      ? String(photo.name)
      : null;

  const input: CreateOnboardingInput = {
    phone: String(form.get("phone") ?? "").trim(),
    businessName: String(form.get("businessName") ?? "").trim(),
    category: String(form.get("category") ?? "").trim(),
    city: String(form.get("city") ?? "").trim(),
    bio: String(form.get("bio") ?? "").trim(),
    photoName,
  };
  validate(input);

  const record = saveOnboarding({
    id: randomUUID(),
    ...input,
    status: "profile_collected",
    createdAt: new Date().toISOString(),
  });
  return { record, source: "local_fallback" };
}

export function getOnboardingStatus(id: string): OnboardingRecord {
  const record = getOnboarding(id);
  if (!record) {
    throw new AppError("NOT_FOUND", "Onboarding profile not found", 404);
  }
  return record;
}

export function markOAuthConnected(id: string | undefined): void {
  if (!id) return;
  const existing = getOnboarding(id);
  if (existing) {
    saveOnboarding({ ...existing, status: "oauth_connected" });
  }
}

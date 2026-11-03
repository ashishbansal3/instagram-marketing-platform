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
import { CORRELATION_HEADER } from "@/lib/correlation";

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

function formToSpringJson(form: FormData): Record<string, unknown> {
  const phone = String(form.get("phone") ?? "").trim();
  return {
    phoneE164: phone,
    phone,
    businessName: String(form.get("businessName") ?? "").trim(),
    category: String(form.get("category") ?? "").trim(),
    city: String(form.get("city") ?? "").trim(),
    bio: String(form.get("bio") ?? "").trim(),
    services: String(form.get("services") ?? "").trim() || null,
    address: String(form.get("address") ?? "").trim() || null,
    hours: String(form.get("hours") ?? "").trim() || null,
    locale: "en-IN",
  };
}

async function forwardJsonToSpring(
  payload: Record<string, unknown>,
  correlationId?: string,
): Promise<Response> {
  const headers: Record<string, string> = {
    "content-type": "application/json",
  };
  if (correlationId) {
    headers[CORRELATION_HEADER] = correlationId;
  }
  return fetch(`${env.springApiUrl}/api/v1/onboarding`, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  });
}

export async function createOnboardingFromFormData(
  form: FormData,
  correlationId?: string,
): Promise<{ record: OnboardingRecord; source: "spring" | "local_fallback" }> {
  const photo = form.get("photo");
  const photoName =
    photo && typeof photo === "object" && "name" in photo && "size" in photo && photo.size > 0
      ? String(photo.name)
      : null;

  if (env.springApiUrl) {
    try {
      const payload = formToSpringJson(form);
      const upstream = await forwardJsonToSpring(payload, correlationId);
      if (upstream.ok) {
        const data = (await upstream.json()) as {
          id?: string;
          clientId?: string;
          phoneE164?: string;
          phone?: string;
          businessName?: string;
          category?: string;
          city?: string;
          bio?: string;
          status?: string;
          onboardingStatus?: string;
          websitePreviewUrl?: string | null;
          websiteStatus?: string | null;
          setupJobId?: string;
        };
        const id = data.id ?? data.clientId;
        if (!id) {
          throw new AppError("UPSTREAM_UNAVAILABLE", "Onboarding API missing id", 502);
        }
        const record: OnboardingRecord = {
          id,
          phone: String(data.phone ?? data.phoneE164 ?? form.get("phone") ?? ""),
          businessName: String(data.businessName ?? form.get("businessName") ?? ""),
          category: String(data.category ?? form.get("category") ?? ""),
          city: String(data.city ?? form.get("city") ?? ""),
          bio: String(data.bio ?? form.get("bio") ?? ""),
          photoName,
          status:
            (data.status as OnboardingRecord["status"]) ??
            (data.onboardingStatus as OnboardingRecord["status"]) ??
            "profile_collected",
          createdAt: new Date().toISOString(),
          websitePreviewUrl: data.websitePreviewUrl ?? null,
          websiteStatus: data.websiteStatus ?? "pending",
          setupJobId: data.setupJobId,
        };
        saveOnboarding(record);
        return { record, source: "spring" };
      }
    } catch (err) {
      if (err instanceof AppError) throw err;
      if (env.metaMode === "live") {
        throw new AppError(
          "UPSTREAM_UNAVAILABLE",
          "Onboarding API unavailable",
          502,
        );
      }
    }
  }

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
    websitePreviewUrl: null,
    websiteStatus: "pending_local",
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

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
import { newCorrelationId } from "@/lib/api-client";

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

type SpringOnboardResponse = {
  clientId?: string;
  tenantId?: string;
  onboardingStatus?: string;
  phoneE164?: string;
  waUserId?: string;
  tenantSlug?: string;
  id?: string;
};

/** Map browser FormData fields → Spring /api/v1/onboarding JSON body. */
function springPayloadFromForm(form: FormData): Record<string, string> {
  return {
    phoneE164: String(form.get("phone") ?? "").trim(),
    businessName: String(form.get("businessName") ?? "").trim(),
    category: String(form.get("category") ?? "").trim(),
    city: String(form.get("city") ?? "").trim(),
    bio: String(form.get("bio") ?? "").trim(),
    locale: "en-IN",
  };
}

async function forwardToSpring(form: FormData): Promise<Response> {
  const payload = springPayloadFromForm(form);
  const correlationId = newCorrelationId();
  return fetch(`${env.springApiUrl}/api/v1/onboarding`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      [CORRELATION_HEADER]: correlationId,
    },
    body: JSON.stringify(payload),
  });
}

export async function createOnboardingFromFormData(
  form: FormData,
): Promise<{ record: OnboardingRecord; source: "spring" | "local_fallback" }> {
  if (env.springApiUrl) {
    try {
      const upstream = await forwardToSpring(form);
      const data = (await upstream.json().catch(() => ({}))) as SpringOnboardResponse & {
        title?: string;
        detail?: string;
        message?: string;
      };
      if (upstream.ok) {
        const id = data.clientId || data.id;
        if (!id) {
          throw new AppError(
            "UPSTREAM_INVALID",
            "Spring onboarding response missing clientId",
            502,
          );
        }
        const record: OnboardingRecord = {
          id,
          phone: String(data.phoneE164 ?? form.get("phone") ?? ""),
          businessName: String(
            form.get("businessName") ?? data.tenantSlug ?? "",
          ),
          category: String(form.get("category") ?? ""),
          city: String(form.get("city") ?? ""),
          bio: String(form.get("bio") ?? ""),
          photoName: null,
          status:
            (data.onboardingStatus as OnboardingRecord["status"]) ??
            "profile_collected",
          createdAt: new Date().toISOString(),
        };
        // Keep a local mirror for connect/status UX when Spring is the source of truth.
        saveOnboarding(record);
        return { record, source: "spring" };
      }
      if (env.metaMode === "live") {
        throw new AppError(
          "UPSTREAM_ERROR",
          data.detail || data.message || data.title || "Onboarding API error",
          upstream.status >= 400 && upstream.status < 600 ? upstream.status : 502,
        );
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
      // META_MODE=mock: fall through to local only if Spring is unreachable.
    }
  }

  const photo = form.get("photo");
  const photoName =
    photo &&
    typeof photo === "object" &&
    "name" in photo &&
    "size" in photo &&
    photo.size > 0
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

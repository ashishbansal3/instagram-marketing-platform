import type { OnboardingRecord } from "@/domain/onboarding";

/**
 * In-process store used only when SPRING_API_URL is unset (local BFF fallback).
 * Production deployments should set SPRING_API_URL so the Spring API is source of truth.
 */
const g = globalThis as unknown as {
  __rozpostOnboardings?: Map<string, OnboardingRecord>;
};

function store(): Map<string, OnboardingRecord> {
  if (!g.__rozpostOnboardings) {
    g.__rozpostOnboardings = new Map();
  }
  return g.__rozpostOnboardings;
}

export function saveOnboarding(record: OnboardingRecord): OnboardingRecord {
  store().set(record.id, record);
  return record;
}

export function getOnboarding(id: string): OnboardingRecord | undefined {
  return store().get(id);
}

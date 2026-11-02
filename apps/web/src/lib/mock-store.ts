export type OnboardingRecord = {
  id: string;
  phone: string;
  businessName: string;
  category: string;
  city: string;
  bio: string;
  photoName: string | null;
  status: "profile_collected" | "oauth_pending" | "oauth_connected";
  createdAt: string;
};

const g = globalThis as unknown as {
  __rozpostOnboardings?: Map<string, OnboardingRecord>;
};

function store() {
  if (!g.__rozpostOnboardings) g.__rozpostOnboardings = new Map();
  return g.__rozpostOnboardings;
}

export function saveOnboarding(record: OnboardingRecord) {
  store().set(record.id, record);
  return record;
}

export function getOnboarding(id: string) {
  return store().get(id);
}

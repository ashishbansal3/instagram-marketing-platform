export type OnboardingStatus =
  | "profile_collected"
  | "oauth_pending"
  | "oauth_connected";

export type OnboardingRecord = {
  id: string;
  phone: string;
  businessName: string;
  category: string;
  city: string;
  bio: string;
  photoName: string | null;
  status: OnboardingStatus;
  createdAt: string;
};

export type CreateOnboardingInput = {
  phone: string;
  businessName: string;
  category: string;
  city: string;
  bio: string;
  photoName: string | null;
};

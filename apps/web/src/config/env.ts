/**
 * Externalized runtime config. Prefer env vars; never hardcode secrets or tenant ids.
 */
function requiredInLive(name: string, value: string | undefined): string | undefined {
  if (process.env.META_MODE === "live" && !value) {
    throw new Error(`Missing required env ${name} when META_MODE=live`);
  }
  return value;
}

export const env = {
  siteUrl: (process.env.NEXT_PUBLIC_SITE_URL ?? "https://rozpost.com").replace(
    /\/$/,
    "",
  ),
  /** mock = local/dev without Meta; live = real Instagram Business Login */
  metaMode: (process.env.META_MODE ??
    process.env.NEXT_PUBLIC_META_MODE ??
    "mock") as "mock" | "live",
  springApiUrl: process.env.SPRING_API_URL?.replace(/\/$/, "") ?? "",
  instagramOAuthStartUrl:
    process.env.NEXT_PUBLIC_INSTAGRAM_OAUTH_START_URL ??
    "/api/v1/oauth/instagram/start",
  get isMockMeta() {
    return this.metaMode !== "live";
  },
} as const;

export function assertLiveOAuthConfig(): void {
  requiredInLive(
    "NEXT_PUBLIC_INSTAGRAM_OAUTH_START_URL",
    process.env.NEXT_PUBLIC_INSTAGRAM_OAUTH_START_URL,
  );
}

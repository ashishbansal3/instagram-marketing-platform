/**
 * Browser/server fetch helper: /api/v1 base + X-Correlation-Id propagation.
 */
export function newCorrelationId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `web_${Date.now()}_${Math.random().toString(16).slice(2)}`;
}

export type ApiErrorBody = {
  error?: {
    code?: string;
    message?: string;
    correlationId?: string;
  };
};

export async function apiFetch(
  path: string,
  init: RequestInit = {},
  correlationId = newCorrelationId(),
): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("X-Correlation-Id", correlationId);
  const url = path.startsWith("/api/v1") ? path : `/api/v1${path.startsWith("/") ? path : `/${path}`}`;
  return fetch(url, { ...init, headers });
}

export function errorMessageFromBody(data: ApiErrorBody, fallback: string): string {
  return data.error?.message || fallback;
}

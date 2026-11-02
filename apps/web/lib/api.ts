const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export function newCorrelationId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `web-${Date.now()}`;
}

export async function apiGetV1<T>(path: string, correlationId = newCorrelationId()): Promise<T> {
  const res = await fetch(`${API_BASE}/api/v1${path}`, {
    headers: { "X-Correlation-Id": correlationId, Accept: "application/json" },
    cache: "no-store"
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`api_error status=${res.status} body=${body}`);
  }
  return res.json() as Promise<T>;
}

export { API_BASE };

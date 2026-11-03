import { randomUUID } from "crypto";
import type { NextRequest } from "next/server";

export const CORRELATION_HEADER = "x-correlation-id";

export function getCorrelationId(req: NextRequest): string {
  const incoming = req.headers.get(CORRELATION_HEADER)?.trim();
  return incoming && incoming.length > 0 ? incoming : randomUUID();
}

export type ErrorCode =
  | "VALIDATION_ERROR"
  | "NOT_FOUND"
  | "UPSTREAM_UNAVAILABLE"
  | "UPSTREAM_INVALID"
  | "UPSTREAM_ERROR"
  | "UNAUTHORIZED"
  | "INTERNAL";

export class AppError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly details?: Record<string, unknown>;

  constructor(
    code: ErrorCode,
    message: string,
    status: number,
    details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "AppError";
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

export function toErrorBody(err: unknown, correlationId: string) {
  if (err instanceof AppError) {
    return {
      error: {
        code: err.code,
        message: err.message,
        details: err.details,
        correlationId,
      },
    };
  }
  return {
    error: {
      code: "INTERNAL" as const,
      message: "Unexpected server error",
      correlationId,
    },
  };
}

export function statusFor(err: unknown): number {
  return err instanceof AppError ? err.status : 500;
}

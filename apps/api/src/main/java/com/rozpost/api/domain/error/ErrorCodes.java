package com.rozpost.api.domain.error;

/** Stable RFC-7807 ProblemDetail {@code code} values for clients and Sentry fingerprints. */
public final class ErrorCodes {
    public static final String WA_SIGNATURE_INVALID = "WA_SIGNATURE_INVALID";
    public static final String QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED";
    public static final String NEEDS_REAUTH = "NEEDS_REAUTH";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String MOCK_ONLY = "MOCK_ONLY";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String PLAN_BLOCKED = "PLAN_BLOCKED";

    private ErrorCodes() {}
}

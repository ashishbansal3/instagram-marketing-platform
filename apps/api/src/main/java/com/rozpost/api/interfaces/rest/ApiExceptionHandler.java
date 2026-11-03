package com.rozpost.api.interfaces.rest;

import com.rozpost.api.domain.error.ErrorCodes;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import com.rozpost.api.interfaces.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single RFC-7807 ProblemDetail advice for the API.
 * Stable {@code code} property for clients / Sentry fingerprints.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail apiException(ApiException ex) {
        String cid = CorrelationIdFilter.current();
        log.warn("typed_error code={} correlationId={} message={}", ex.getCode(), cid, ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getStatus().getReasonPhrase());
        pd.setProperty("code", normalizeCode(ex.getCode()));
        pd.setProperty("correlationId", cid);
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail illegalState(IllegalStateException ex) {
        String cid = CorrelationIdFilter.current();
        String msg = ex.getMessage() == null ? "conflict" : ex.getMessage();
        String code = ErrorCodes.CONFLICT;
        HttpStatus status = HttpStatus.CONFLICT;
        String lower = msg.toLowerCase();
        if (lower.contains("quota")) {
            code = ErrorCodes.QUOTA_EXHAUSTED;
        } else if (lower.contains("reauth") || lower.contains("reconnect")) {
            code = ErrorCodes.NEEDS_REAUTH;
            status = HttpStatus.UNAUTHORIZED;
        } else if (lower.contains("plan")) {
            code = ErrorCodes.PLAN_BLOCKED;
            status = HttpStatus.PAYMENT_REQUIRED;
        }
        log.warn("typed_error code={} correlationId={} message={}", code, cid, msg);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, msg);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("correlationId", cid);
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail illegalArg(IllegalArgumentException ex) {
        String cid = CorrelationIdFilter.current();
        String msg = ex.getMessage() == null ? "bad_request" : ex.getMessage();
        HttpStatus status = msg.toLowerCase().contains("not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String code = status == HttpStatus.NOT_FOUND ? ErrorCodes.NOT_FOUND : ErrorCodes.BAD_REQUEST;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, msg);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("correlationId", cid);
        return pd;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail responseStatus(ResponseStatusException ex) {
        String cid = CorrelationIdFilter.current();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                ex.getStatusCode(),
                ex.getReason() == null ? ex.getMessage() : ex.getReason());
        pd.setTitle(ex.getStatusCode().toString());
        String code = ex.getStatusCode().value() == 403 ? ErrorCodes.MOCK_ONLY : "HTTP_ERROR";
        pd.setProperty("code", code);
        pd.setProperty("correlationId", cid);
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail generic(Exception ex) {
        String cid = CorrelationIdFilter.current();
        log.error("unhandled_error correlationId={}", cid, ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");
        pd.setTitle("Internal Server Error");
        pd.setProperty("code", ErrorCodes.INTERNAL_ERROR);
        pd.setProperty("correlationId", cid);
        return pd;
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return ErrorCodes.INTERNAL_ERROR;
        }
        return switch (code) {
            case "invalid_signature", "wa_signature_invalid" -> ErrorCodes.WA_SIGNATURE_INVALID;
            case "quota_exhausted" -> ErrorCodes.QUOTA_EXHAUSTED;
            case "needs_reauth" -> ErrorCodes.NEEDS_REAUTH;
            case "bad_request" -> ErrorCodes.BAD_REQUEST;
            case "not_found", "post_not_found" -> ErrorCodes.NOT_FOUND;
            case "conflict", "invalid_post_status" -> ErrorCodes.CONFLICT;
            case "mock_only" -> ErrorCodes.MOCK_ONLY;
            case "plan_blocked" -> ErrorCodes.PLAN_BLOCKED;
            default -> code.contains("_") && code.equals(code.toUpperCase()) ? code : code.toUpperCase();
        };
    }
}

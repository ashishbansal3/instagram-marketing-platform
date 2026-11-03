package com.rozpost.api.interfaces.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail illegalState(IllegalStateException ex) {
        log.warn("typed_error code=conflict message={}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflict");
        pd.setProperty("code", "conflict");
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail illegalArg(IllegalArgumentException ex) {
        String msg = ex.getMessage() == null ? "bad_request" : ex.getMessage();
        HttpStatus status = msg.toLowerCase().contains("not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, msg);
        pd.setTitle(status == HttpStatus.NOT_FOUND ? "Not Found" : "Bad Request");
        pd.setProperty("code", status == HttpStatus.NOT_FOUND ? "not_found" : "bad_request");
        return pd;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail responseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(),
                ex.getReason() == null ? ex.getMessage() : ex.getReason());
        pd.setTitle(ex.getStatusCode().toString());
        pd.setProperty("code", ex.getStatusCode().value() == 403 ? "mock_only" : "http_error");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail generic(Exception ex) {
        log.error("unhandled_error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");
        pd.setTitle("Internal Server Error");
        pd.setProperty("code", "internal_error");
        return pd;
    }
}

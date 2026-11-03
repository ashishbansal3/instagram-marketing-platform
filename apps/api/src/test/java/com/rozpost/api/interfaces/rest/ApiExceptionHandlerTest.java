package com.rozpost.api.interfaces.rest;

import com.rozpost.api.domain.error.ErrorCodes;
import com.rozpost.api.interfaces.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void apiExceptionMapsStableCodeAndCorrelationProperty() {
        ProblemDetail pd = handler.apiException(
                new ApiException("needs_reauth", "reconnect Instagram", HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), pd.getStatus());
        assertEquals(ErrorCodes.NEEDS_REAUTH, pd.getProperties().get("code"));
    }

    @Test
    void illegalStateQuotaMapsQuotaExhausted() {
        ProblemDetail pd = handler.illegalState(new IllegalStateException("quota exhausted for ig user"));
        assertEquals(ErrorCodes.QUOTA_EXHAUSTED, pd.getProperties().get("code"));
        assertEquals(HttpStatus.CONFLICT.value(), pd.getStatus());
    }

    @Test
    void illegalArgNotFound() {
        ProblemDetail pd = handler.illegalArg(new IllegalArgumentException("post not found"));
        assertEquals(ErrorCodes.NOT_FOUND, pd.getProperties().get("code"));
        assertEquals(HttpStatus.NOT_FOUND.value(), pd.getStatus());
    }
}

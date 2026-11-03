package com.rozpost.api.interfaces.error;

import java.time.Instant;

public record ErrorResponse(
    String code, String message, String correlationId, Instant timestamp) {
  public static ErrorResponse of(String code, String message, String correlationId) {
    return new ErrorResponse(code, message, correlationId, Instant.now());
  }
}

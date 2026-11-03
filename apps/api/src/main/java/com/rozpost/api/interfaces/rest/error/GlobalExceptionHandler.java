package com.rozpost.api.interfaces.rest.error;

import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import com.rozpost.api.interfaces.error.ApiException;
import com.rozpost.api.interfaces.error.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    String cid = CorrelationIdFilter.current();
    log.warn("api_error code={} correlationId={} msg={}", e.getCode(), cid, e.getMessage());
    return ResponseEntity.status(e.getStatus())
        .body(ErrorResponse.of(e.getCode(), e.getMessage(), cid));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
    String cid = CorrelationIdFilter.current();
    return ResponseEntity.badRequest().body(ErrorResponse.of("bad_request", e.getMessage(), cid));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
    String cid = CorrelationIdFilter.current();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("conflict", e.getMessage(), cid));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleOther(Exception e) {
    String cid = CorrelationIdFilter.current();
    log.error("unhandled correlationId={}", cid, e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("internal_error", "unexpected error", cid));
  }
}

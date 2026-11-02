package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record Disapproved(
    UUID tenantId,
    String correlationId,
    UUID postId,
    int retryCount,
    boolean manual,
    Instant occurredAt
) implements DomainEvent {
  @Override
  public String type() {
    return "Disapproved";
  }
}

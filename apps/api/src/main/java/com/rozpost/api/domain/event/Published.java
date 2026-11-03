package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record Published(
    UUID tenantId,
    String correlationId,
    UUID postId,
    String igMediaId,
    String igPermalink,
    Instant occurredAt
) implements DomainEvent {
  @Override
  public String type() {
    return "Published";
  }
}

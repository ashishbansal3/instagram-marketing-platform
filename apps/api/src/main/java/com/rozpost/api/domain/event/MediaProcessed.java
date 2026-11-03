package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MediaProcessed(
    UUID tenantId,
    String correlationId,
    UUID postId,
    String outputAssetId,
    Instant occurredAt
) implements DomainEvent {
  @Override
  public String type() {
    return "MediaProcessed";
  }
}

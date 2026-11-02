package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record Approved(
        UUID tenantId,
        String correlationId,
        String wamid,
        String postKey,
        Instant occurredAt
) implements DomainEvent {
    @Override public String type() { return "Approved"; }
}

package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MediaReceived(
        UUID tenantId,
        String correlationId,
        String wamid,
        String mediaId,
        String instructionText,
        Instant occurredAt
) implements DomainEvent {
    @Override public String type() { return "MediaReceived"; }
}

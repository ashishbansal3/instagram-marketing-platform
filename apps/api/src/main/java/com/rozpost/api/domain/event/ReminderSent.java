package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReminderSent(
        UUID tenantId,
        String correlationId,
        UUID postId,
        String channel,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String type() {
        return "ReminderSent";
    }
}

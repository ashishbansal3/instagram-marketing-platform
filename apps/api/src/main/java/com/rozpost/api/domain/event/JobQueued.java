package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record JobQueued(
        UUID tenantId,
        String correlationId,
        UUID jobId,
        String jobType,
        String idempotencyKey,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String type() {
        return "JobQueued";
    }
}

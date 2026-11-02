package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    String type();
    UUID tenantId();
    String correlationId();
    Instant occurredAt();
}

package com.rozpost.api.domain.event;

import java.time.Instant;
import java.util.UUID;

public record OnboardingCompleted(
        UUID tenantId,
        UUID clientId,
        String tenantSlug,
        String businessName,
        String category,
        String city,
        String phoneE164,
        String bio,
        String services,
        String address,
        String hoursJson,
        Double latitude,
        Double longitude,
        String correlationId,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String type() {
        return "OnboardingCompleted";
    }
}

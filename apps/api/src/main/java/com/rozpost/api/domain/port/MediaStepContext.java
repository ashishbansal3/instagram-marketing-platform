package com.rozpost.api.domain.port;

import java.util.Map;
import java.util.UUID;

public record MediaStepContext(
        UUID tenantId,
        String correlationId,
        String postId,
        Map<String, String> attributes
) {}

package com.rozpost.api.domain.port;

import java.util.Map;
import java.util.UUID;

public interface JobEnqueuePort {
    UUID enqueue(
            UUID tenantId,
            UUID clientId,
            String type,
            String queueName,
            String idempotencyKey,
            Map<String, Object> payload);
}

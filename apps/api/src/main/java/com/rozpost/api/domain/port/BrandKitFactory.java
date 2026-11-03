package com.rozpost.api.domain.port;

import java.util.Map;
import java.util.UUID;

/** Factory port for per-tenant brand kit resolution (Open/Closed). */
public interface BrandKitFactory {
    Map<String, Object> forTenant(UUID tenantId);
}

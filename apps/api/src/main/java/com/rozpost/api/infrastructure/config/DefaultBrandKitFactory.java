package com.rozpost.api.infrastructure.config;

import com.rozpost.api.domain.port.BrandKitFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class DefaultBrandKitFactory implements BrandKitFactory {
    @Override
    public Map<String, Object> forTenant(UUID tenantId) {
        return Map.of(
                "tenantId", tenantId.toString(),
                "palette", "warm-neutral",
                "scriptPrefer", "hi-IN"
        );
    }
}

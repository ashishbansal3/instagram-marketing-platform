package com.rozpost.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @Value("${rozpost.meta-mode:mock}")
    private String metaMode;

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "rozpost-api",
                "ok", true,
                "metaMode", metaMode
        );
    }
}

package com.rozpost.api.infrastructure.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.port.MetaGraphClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * META_MODE=mock Graph client — reads {@code packages/meta-mocks} fixture (or classpath fallback).
 * Never hardcodes {@code quota_total}.
 */
@Component
public class MockMetaGraphClient implements MetaGraphClient {

    private final ObjectMapper mapper;

    public MockMetaGraphClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Object> contentPublishingLimit(String igUserId) {
        try {
            JsonNode root = readFixture();
            JsonNode data = root.path("response").path("data");
            if (!data.isArray() || data.isEmpty()) {
                data = root.path("data");
            }
            JsonNode row = data.isArray() && !data.isEmpty() ? data.get(0) : root;
            long usage = row.path("quota_usage").asLong(-1);
            long total = row.path("config").path("quota_total").asLong(-1);
            long duration = row.path("config").path("quota_duration").asLong(86400);
            if (usage < 0 || total <= 0) {
                throw new IllegalStateException("mock fixture missing quota_usage/config.quota_total");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("igUserId", igUserId);
            out.put("quota_usage", usage);
            out.put("quota_total", total);
            out.put("quota_duration", duration);
            out.put("source", "fixture");
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load content_publishing_limit fixture", e);
        }
    }

    private JsonNode readFixture() throws Exception {
        Path repo = Path.of("packages/meta-mocks/fixtures/instagram/content-publishing-limit.json");
        if (Files.isRegularFile(repo)) {
            return mapper.readTree(Files.readString(repo));
        }
        ClassPathResource cpr = new ClassPathResource(
                "fixtures/instagram/content-publishing-limit.json");
        if (cpr.exists()) {
            try (InputStream in = cpr.getInputStream()) {
                return mapper.readTree(in);
            }
        }
        // Minimal embedded fixture when working directory is apps/api in CI/tests.
        return mapper.readTree(
                "{\"response\":{\"data\":[{\"quota_usage\":3,\"config\":{\"quota_total\":25,\"quota_duration\":86400}}]}}");
    }
}

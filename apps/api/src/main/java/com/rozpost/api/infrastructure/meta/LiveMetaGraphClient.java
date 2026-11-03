package com.rozpost.api.infrastructure.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.port.MetaGraphClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live Graph client for Instagram content_publishing_limit.
 * Requires a caller-supplied page/user token via thread-local or future TokenVault — MVP uses env
 * {@code META_GRAPH_ACCESS_TOKEN} placeholder (never hardcode secrets).
 */
@Component
public class LiveMetaGraphClient implements MetaGraphClient {

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String graphVersion;
    private final String accessTokenEnv;

    public LiveMetaGraphClient(
            ObjectMapper mapper,
            @Value("${rozpost.meta-graph-version:v21.0}") String graphVersion,
            @Value("${rozpost.meta-graph-access-token:}") String accessTokenEnv
    ) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.graphVersion = graphVersion;
        this.accessTokenEnv = accessTokenEnv;
    }

    @Override
    public Map<String, Object> contentPublishingLimit(String igUserId) {
        if (accessTokenEnv == null || accessTokenEnv.isBlank()) {
            throw new IllegalStateException(
                    "META_GRAPH_ACCESS_TOKEN / rozpost.meta-graph-access-token required in live mode");
        }
        try {
            String url = "https://graph.facebook.com/" + graphVersion + "/" + igUserId
                    + "/content_publishing_limit?fields=quota_usage,config&access_token="
                    + accessTokenEnv;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new IllegalStateException("Graph content_publishing_limit HTTP " + res.statusCode());
            }
            JsonNode root = mapper.readTree(res.body());
            JsonNode data = root.path("data");
            JsonNode row = data.isArray() && !data.isEmpty() ? data.get(0) : root;
            long usage = row.path("quota_usage").asLong(-1);
            long total = row.path("config").path("quota_total").asLong(-1);
            long duration = row.path("config").path("quota_duration").asLong(86400);
            if (usage < 0 || total <= 0) {
                throw new IllegalStateException(
                        "content_publishing_limit missing config.quota_total — refusing hardcoded fallback");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("igUserId", igUserId);
            out.put("quota_usage", usage);
            out.put("quota_total", total);
            out.put("quota_duration", duration);
            out.put("source", "live");
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Live Graph quota lookup failed", e);
        }
    }
}

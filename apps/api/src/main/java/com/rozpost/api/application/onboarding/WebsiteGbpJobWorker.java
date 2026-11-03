package com.rozpost.api.application.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code stream.website-gbp} (and bridges the legacy list) so onboarding HTTP stays fast.
 */
@Component
public class WebsiteGbpJobWorker {
    private static final Logger log = LoggerFactory.getLogger(WebsiteGbpJobWorker.class);
    static final String LIST_KEY = "website-gbp";
    static final String STREAM_KEY = "stream.website-gbp";
    static final String GROUP = "api-website-gbp";
    static final String CONSUMER = "api-1";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final WebsiteGbpSetupService setup;
    private final JdbcTemplate jdbc;
    private boolean groupReady;

    public WebsiteGbpJobWorker(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            WebsiteGbpSetupService setup,
            JdbcTemplate jdbc) {
        this.redis = redis;
        this.mapper = mapper;
        this.setup = setup;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${rozpost.website.job-poll-ms:1500}")
    public void poll() {
        bridgeListToStream();
        ensureGroup();
        try {
            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> records =
                    redis.opsForStream()
                            .read(
                                    org.springframework.data.redis.connection.stream.Consumer.from(
                                            GROUP, CONSUMER),
                                    StreamReadOptions.empty().count(5).block(Duration.ofMillis(200)),
                                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
            if (records == null || records.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    processRecord(record.getValue());
                    redis.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
                } catch (Exception e) {
                    log.error("website-gbp job failed id={}: {}", record.getId(), e.getMessage());
                    markJobFailed(record.getValue(), e.getMessage());
                    redis.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
                }
            }
        } catch (Exception e) {
            if (!isEmptyGroupBusy(e)) {
                log.debug("website-gbp poll: {}", e.getMessage());
            }
        }
    }

    private void bridgeListToStream() {
        for (int i = 0; i < 10; i++) {
            String json = redis.opsForList().rightPop(LIST_KEY);
            if (json == null) {
                break;
            }
            try {
                JsonNode node = mapper.readTree(json);
                Map<String, String> fields = new HashMap<>();
                fields.put("payload", json);
                fields.put("jobId", text(node, "jobId"));
                fields.put("type", text(node, "type"));
                JsonNode payload = node.path("payload");
                fields.put(
                        "correlationId",
                        payload.path("correlationId").asText(""));
                redis.opsForStream().add(STREAM_KEY, fields);
            } catch (Exception e) {
                log.warn("Failed to bridge website-gbp list item: {}", e.getMessage());
            }
        }
    }

    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            redis.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
            groupReady = true;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("BUSYGROUP") || msg.contains("already exists")) {
                groupReady = true;
            } else {
                // Stream may not exist yet — create via MKSTREAM equivalent by adding a noop then deleting is messy;
                // Spring createGroup with non-existent stream fails; seed empty stream.
                try {
                    redis.opsForStream().add(STREAM_KEY, Map.of("init", "1"));
                    redis.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
                    groupReady = true;
                } catch (Exception e2) {
                    String m2 = e2.getMessage() == null ? "" : e2.getMessage();
                    if (m2.contains("BUSYGROUP") || m2.contains("already exists")) {
                        groupReady = true;
                    } else {
                        log.debug("website-gbp group not ready: {}", e2.getMessage());
                    }
                }
            }
        }
    }

    private void processRecord(Map<Object, Object> value) throws Exception {
        Object payloadField = value.get("payload");
        if (payloadField == null) {
            throw new IllegalArgumentException("missing payload field");
        }
        JsonNode envelope = mapper.readTree(payloadField.toString());
        String jobId = envelope.path("jobId").asText(null);
        UUID tenantId = UUID.fromString(envelope.path("tenantId").asText());
        UUID clientId = UUID.fromString(envelope.path("clientId").asText());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.convertValue(envelope.path("payload"), Map.class);

        if (jobId != null) {
            jdbc.update(
                    "UPDATE jobs SET status = 'running', attempts = attempts + 1, updated_at = now() WHERE id = ?::uuid",
                    jobId);
        }

        Map<String, Object> result = setup.run(tenantId, clientId, payload == null ? Map.of() : payload);

        if (jobId != null) {
            jdbc.update(
                    """
                    UPDATE jobs SET status = 'succeeded', result = ?::jsonb, updated_at = now(), last_error = NULL
                    WHERE id = ?::uuid
                    """,
                    mapper.writeValueAsString(result),
                    jobId);
        }
    }

    private void markJobFailed(Map<Object, Object> value, String error) {
        try {
            Object payloadField = value.get("payload");
            if (payloadField == null) {
                return;
            }
            JsonNode envelope = mapper.readTree(payloadField.toString());
            String jobId = envelope.path("jobId").asText(null);
            if (jobId != null) {
                jdbc.update(
                        "UPDATE jobs SET status = 'failed', last_error = ?, updated_at = now() WHERE id = ?::uuid",
                        error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500)),
                        jobId);
            }
            String clientId = envelope.path("clientId").asText(null);
            if (clientId != null) {
                jdbc.update(
                        """
                        UPDATE business_websites SET status = 'failed', error_message = ?, updated_at = now()
                        WHERE client_id = ?::uuid
                        """,
                        error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500)),
                        clientId);
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static boolean isEmptyGroupBusy(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("NOGROUP") || msg.contains("no such key"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText();
    }
}

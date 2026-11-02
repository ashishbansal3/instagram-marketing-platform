package com.rozpost.api.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rozpost.api.domain.event.Approved;
import com.rozpost.api.domain.event.MediaReceived;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WhatsAppIngestService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppIngestService.class);

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final DomainEventBus eventBus;

    public WhatsAppIngestService(
            ObjectMapper mapper,
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            DomainEventBus eventBus
    ) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.redis = redis;
        this.eventBus = eventBus;
    }

    public Map<String, Object> ingest(JsonNode root) {
        String correlationId = Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY)).orElse("unknown");
        JsonNode message = root.path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        if (message.isMissingNode() || message.isNull()) {
            return Map.of("status", "ignored", "reason", "no message");
        }

        String wamid = text(message, "id").orElse(UUID.randomUUID().toString());
        String from = text(message, "from").orElse("unknown");
        String type = text(message, "type").orElse("unknown");

        if ("interactive".equals(type)) {
            String buttonId = text(message.path("interactive").path("button_reply"), "id").orElse("");
            String decision = buttonId.startsWith("approve_") ? "approved"
                    : buttonId.startsWith("disapprove_") ? "disapproved" : "unknown";
            redis.opsForStream().add("approvals", Map.of(
                    "wamid", wamid,
                    "from", from,
                    "decision", decision,
                    "buttonId", buttonId,
                    "correlationId", correlationId
            ));
            if ("approved".equals(decision)) {
                eventBus.publish(new Approved(
                        resolveTenantId(from),
                        correlationId,
                        wamid,
                        buttonId.replaceFirst("^approve_post_", ""),
                        Instant.now()
                ));
            }
            return Map.of("status", "queued_approval", "decision", decision, "wamid", wamid);
        }

        String instructionText = null;
        String mediaId = null;
        if ("image".equals(type)) {
            instructionText = text(message.path("image"), "caption").orElse(null);
            mediaId = text(message.path("image"), "id").orElse(null);
        }

        UUID tenantId = resolveTenantId(from);
        ObjectNode payload = mapper.createObjectNode()
                .put("from", from)
                .put("type", type)
                .put("mediaId", mediaId)
                .put("instruction_text", instructionText)
                .put("correlationId", correlationId);

        jdbc.update(
                """
                INSERT INTO jobs (tenant_id, type, status, idempotency_key, payload)
                VALUES (?::uuid, 'media_download', 'queued', ?, ?::jsonb)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                tenantId.toString(),
                wamid,
                payload.toString()
        );

        redis.opsForStream().add("media-download", Map.of(
                "wamid", wamid,
                "from", from,
                "mediaId", mediaId == null ? "" : mediaId,
                "instruction_text", instructionText == null ? "" : instructionText,
                "tenant_id", tenantId.toString(),
                "correlationId", correlationId
        ));

        eventBus.publish(new MediaReceived(
                tenantId, correlationId, wamid, mediaId, instructionText, Instant.now()
        ));

        log.info("wa_ingest wamid={} type={} instructionPresent={}", wamid, type, instructionText != null);
        return Map.of(
                "status", "queued_media_download",
                "wamid", wamid,
                "instruction_text", instructionText == null ? "" : instructionText
        );
    }

    private UUID resolveTenantId(String waUserId) {
        UUID tenantId = jdbc.query(
                "SELECT tenant_id FROM clients WHERE wa_user_id = ? LIMIT 1",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                waUserId
        );
        if (tenantId == null) {
            throw new IllegalStateException("Unknown WhatsApp sender; complete onboarding first: " + waUserId);
        }
        return tenantId;
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return Optional.empty();
        String s = v.asText();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}

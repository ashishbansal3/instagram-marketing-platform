package com.rozpost.api.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rozpost.api.domain.event.Approved;
import com.rozpost.api.domain.event.JobQueued;
import com.rozpost.api.domain.event.MediaReceived;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable WhatsApp ingest: resolve tenant from WA map (never trust client tenant_id),
 * idempotent receipt + media_assets + jobs, then Redis Stream enqueue + MediaReceived.
 * Returns quickly so webhook can ACK 200 (Meta retries ~7d on failure).
 */
@Service
public class WhatsAppIngestService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppIngestService.class);
    public static final String FIXTURE_INBOUND =
            "packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json";

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final DomainEventBus eventBus;
    private final RozpostProperties props;

    public WhatsAppIngestService(
            ObjectMapper mapper,
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            DomainEventBus eventBus,
            RozpostProperties props
    ) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.redis = redis;
        this.eventBus = eventBus;
        this.props = props;
    }

    public record ClientRef(UUID clientId, UUID tenantId, String waUserId, String status) {}

    @Transactional
    public Map<String, Object> ingest(JsonNode root) {
        return ingest(root, "whatsapp", null);
    }

    @Transactional
    public Map<String, Object> ingest(JsonNode root, String source, String fixturePath) {
        String correlationId = Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY)).orElse("unknown");
        JsonNode message = root.path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        if (message.isMissingNode() || message.isNull()) {
            return Map.of("status", "ignored", "reason", "no_message");
        }

        String wamid = text(message, "id").orElse(UUID.randomUUID().toString());
        String from = text(message, "from").orElse("unknown");
        String type = text(message, "type").orElse("unknown");

        if ("interactive".equals(type)) {
            return handleInteractive(message, wamid, from, correlationId);
        }

        if (!"image".equals(type) && !"video".equals(type)) {
            return Map.of("status", "ignored", "reason", "non_media", "wamid", wamid);
        }

        ClientRef client = resolveClient(from)
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown WhatsApp sender; complete onboarding first: " + from));
        MDC.put("tenantId", client.tenantId().toString());

        if (!tryInsertReceipt("whatsapp", wamid, client.tenantId(), correlationId)) {
            return Map.of("status", "duplicate", "wamid", wamid);
        }

        Integer existingAsset = jdbc.query(
                "SELECT 1 FROM media_assets WHERE wa_message_id = ? LIMIT 1",
                rs -> rs.next() ? 1 : null,
                wamid
        );
        if (existingAsset != null) {
            return Map.of("status", "duplicate_asset", "wamid", wamid);
        }

        if (!"wa_linked".equals(client.status()) && !"active".equals(client.status())) {
            jdbc.update("UPDATE clients SET onboarding_status = 'wa_linked' WHERE id = ?::uuid",
                    client.clientId().toString());
        }

        JsonNode mediaNode = "video".equals(type) ? message.path("video") : message.path("image");
        String instructionText = text(mediaNode, "caption").orElse(null);
        String mediaId = text(mediaNode, "id").orElse(null);
        String mimeType = text(mediaNode, "mime_type")
                .orElse("video".equals(type) ? "video/mp4" : "image/jpeg");
        String kind = "video".equals(type) ? "video" : "image";

        UUID mediaAssetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO media_assets
                  (id, tenant_id, client_id, source, kind, wa_media_id, wa_message_id, mime_type, instruction_text)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                """,
                mediaAssetId.toString(),
                client.tenantId().toString(),
                client.clientId().toString(),
                source,
                kind,
                mediaId,
                wamid,
                mimeType,
                instructionText
        );

        String idempotencyKey = "wamid:" + wamid;
        UUID jobId = UUID.randomUUID();
        int maxAttempts = 5;
        ObjectNode payload = mapper.createObjectNode()
                .put("mediaAssetId", mediaAssetId.toString())
                .put("from", from)
                .put("type", type)
                .put("kind", kind)
                .put("mediaId", mediaId)
                .put("waMessageId", wamid)
                .put("mimeType", mimeType)
                .put("instruction_text", instructionText)
                .put("instructionText", instructionText)
                .put("correlationId", correlationId)
                .put("clientId", client.clientId().toString())
                .put("tenantId", client.tenantId().toString())
                .put("source", source)
                .put("metaMode", props.getMetaMode())
                .put("maxAttempts", maxAttempts);
        if (fixturePath != null) {
            payload.put("fixturePath", fixturePath);
        } else if ("mock".equalsIgnoreCase(props.getMetaMode())) {
            payload.put("fixturePath", FIXTURE_INBOUND);
        }

        int inserted = jdbc.update(
                """
                INSERT INTO jobs (id, tenant_id, type, status, idempotency_key, payload, max_attempts)
                VALUES (?::uuid, ?::uuid, 'media_download', 'queued', ?, ?::jsonb, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                jobId.toString(),
                client.tenantId().toString(),
                idempotencyKey,
                payload.toString(),
                maxAttempts
        );

        if (inserted > 0) {
            Map<String, String> stream = new HashMap<>();
            stream.put("jobId", jobId.toString());
            stream.put("wamid", wamid);
            stream.put("from", from);
            stream.put("mediaId", mediaId == null ? "" : mediaId);
            stream.put("mediaAssetId", mediaAssetId.toString());
            stream.put("instruction_text", instructionText == null ? "" : instructionText);
            stream.put("instructionText", instructionText == null ? "" : instructionText);
            stream.put("tenant_id", client.tenantId().toString());
            stream.put("client_id", client.clientId().toString());
            stream.put("correlationId", correlationId);
            stream.put("metaMode", props.getMetaMode());
            stream.put("source", source);
            stream.put("fixturePath", payload.path("fixturePath").asText(""));
            stream.put("attempts", "0");
            stream.put("maxAttempts", String.valueOf(maxAttempts));
            stream.put("payload", payload.toString());
            redis.opsForStream().add("media-download", stream);
            eventBus.publish(new JobQueued(
                    client.tenantId(),
                    correlationId,
                    jobId,
                    "media_download",
                    idempotencyKey,
                    Instant.now()
            ));
        }

        eventBus.publish(new MediaReceived(
                client.tenantId(), correlationId, wamid, mediaId, instructionText, Instant.now()
        ));

        log.info("wa_ingest wamid={} type={} instructionPresent={} correlationId={}",
                wamid, type, instructionText != null, correlationId);
        return Map.of(
                "status", inserted > 0 ? "queued_media_download" : "duplicate_job",
                "wamid", wamid,
                "mediaAssetId", mediaAssetId.toString(),
                "jobId", jobId.toString(),
                "instruction_text", instructionText == null ? "" : instructionText
        );
    }

    private Map<String, Object> handleInteractive(
            JsonNode message, String wamid, String from, String correlationId) {
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
            resolveClient(from).ifPresent(client -> {
                MDC.put("tenantId", client.tenantId().toString());
                eventBus.publish(new Approved(
                        client.tenantId(),
                        correlationId,
                        wamid,
                        buttonId.replaceFirst("^approve_post_", ""),
                        Instant.now()
                ));
            });
        }
        return Map.of("status", "queued_approval", "decision", decision, "wamid", wamid);
    }

    private boolean tryInsertReceipt(String channel, String externalId, UUID tenantId, String correlationId) {
        try {
            jdbc.update(
                    """
                    INSERT INTO webhook_receipts (channel, external_message_id, tenant_id, correlation_id)
                    VALUES (?, ?, ?::uuid, ?)
                    """,
                    channel, externalId, tenantId.toString(), correlationId
            );
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<ClientRef> resolveClient(String from) {
        if (from == null || from.isBlank()) return Optional.empty();
        String digits = from.replace("+", "");
        return Optional.ofNullable(jdbc.query(
                """
                SELECT id, tenant_id, wa_user_id, onboarding_status FROM clients
                WHERE wa_user_id = ? OR phone_e164 = ? OR phone_e164 = ?
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) return null;
                    return new ClientRef(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("tenant_id"),
                            rs.getString("wa_user_id"),
                            rs.getString("onboarding_status"));
                },
                digits, "+" + digits, from
        ));
    }

    private static Optional<String> text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return Optional.empty();
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return Optional.empty();
        String s = v.asText();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}

package com.rozpost.api.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rozpost.api.application.approval.ApprovalCommandService;
import com.rozpost.api.application.port.JobEnqueuePort;
import com.rozpost.api.application.port.PostRepositoryPort;
import com.rozpost.api.domain.event.JobQueued;
import com.rozpost.api.domain.event.MediaReceived;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.post.PostStatus;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable WhatsApp ingest: resolve tenant from WA map (never trust client tenant_id),
 * idempotent receipt + media_assets + posts + jobs, then Redis list/stream enqueue + MediaReceived.
 * Interactive approve/disapprove buttons route through ApprovalCommandService.
 */
@Service
public class WhatsAppIngestService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppIngestService.class);
    public static final String FIXTURE_INBOUND =
            "packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json";

    private final JdbcTemplate jdbc;
    private final JobEnqueuePort jobs;
    private final DomainEventBus eventBus;
    private final RozpostProperties props;
    private final ApprovalCommandService approval;
    private final PostRepositoryPort posts;

    public WhatsAppIngestService(
            JdbcTemplate jdbc,
            JobEnqueuePort jobs,
            DomainEventBus eventBus,
            RozpostProperties props,
            ApprovalCommandService approval,
            PostRepositoryPort posts
    ) {
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.eventBus = eventBus;
        this.props = props;
        this.approval = approval;
        this.posts = posts;
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
        UUID postId = UUID.randomUUID();
        String r2Key = "tenant/" + client.tenantId() + "/inbound/" + mediaAssetId
                + ("video".equals(kind) ? ".mp4" : ".jpg");

        jdbc.update(
                """
                INSERT INTO media_assets
                  (id, tenant_id, client_id, source, kind, wa_media_id, wa_message_id, mime_type,
                   instruction_text, r2_key)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
                """,
                mediaAssetId.toString(),
                client.tenantId().toString(),
                client.clientId().toString(),
                source,
                kind,
                mediaId,
                wamid,
                mimeType,
                instructionText,
                r2Key
        );

        jdbc.update(
                """
                INSERT INTO posts (id, tenant_id, client_id, type, status, caption, instruction_text,
                  source_asset_ids, retry_count, max_retries)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ARRAY[?::uuid], 0, 3)
                """,
                postId.toString(),
                client.tenantId().toString(),
                client.clientId().toString(),
                kind,
                PostStatus.intake_received.name(),
                instructionText,
                instructionText,
                mediaAssetId.toString()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mediaAssetId", mediaAssetId.toString());
        payload.put("postId", postId.toString());
        payload.put("from", from);
        payload.put("type", type);
        payload.put("kind", kind);
        payload.put("mediaId", mediaId);
        payload.put("waMediaId", mediaId);
        payload.put("waMessageId", wamid);
        payload.put("mimeType", mimeType);
        payload.put("instruction_text", instructionText);
        payload.put("instructionText", instructionText);
        payload.put("correlationId", correlationId);
        payload.put("clientId", client.clientId().toString());
        payload.put("tenantId", client.tenantId().toString());
        payload.put("source", source);
        payload.put("metaMode", props.getMetaMode());
        payload.put("r2Key", r2Key);
        payload.put("locale", "hi-IN");
        payload.put("maxAttempts", 5);

        if (fixturePath != null) {
            // Webhook JSON fixture path is metadata only — never treat as image bytes.
            payload.put("fixturePath", fixturePath);
        } else if (props.isMock()) {
            payload.put("fixturePath", FIXTURE_INBOUND);
        }
        // META_MODE=mock: worker resolves bytes via WhatsAppMediaDownloader(waMediaId).
        // Do not set localPath here — that would mask a missing Graph/mock download stub.

        String idempotencyKey = "wamid:" + wamid;
        UUID jobId = jobs.enqueue(
                client.tenantId(),
                client.clientId(),
                "media_download",
                "media-download",
                idempotencyKey,
                payload
        );

        jdbc.update(
                "UPDATE posts SET status = ?, updated_at = now() WHERE id = ?::uuid",
                PostStatus.media_processing.name(),
                postId.toString()
        );

        eventBus.publish(new JobQueued(
                client.tenantId(),
                correlationId,
                jobId,
                "media_download",
                idempotencyKey,
                Instant.now()
        ));
        eventBus.publish(new MediaReceived(
                client.tenantId(), correlationId, wamid, mediaId, instructionText, Instant.now()
        ));

        log.info("wa_ingest wamid={} type={} postId={} instructionPresent={} correlationId={}",
                wamid, type, postId, instructionText != null, correlationId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "queued_media_download");
        out.put("wamid", wamid);
        out.put("mediaAssetId", mediaAssetId.toString());
        out.put("postId", postId.toString());
        out.put("jobId", jobId.toString());
        out.put("instruction_text", instructionText == null ? "" : instructionText);
        out.put("correlationId", correlationId);
        return out;
    }

    private Map<String, Object> handleInteractive(
            JsonNode message, String wamid, String from, String correlationId) {
        String buttonId = text(message.path("interactive").path("button_reply"), "id").orElse("");
        String title = text(message.path("interactive").path("button_reply"), "title").orElse("");
        String button;
        UUID postId = null;
        if (buttonId.startsWith("approve_post_")) {
            button = "approve";
            postId = parseUuid(buttonId.substring("approve_post_".length()));
        } else if (buttonId.startsWith("disapprove_post_")) {
            button = "disapprove";
            postId = parseUuid(buttonId.substring("disapprove_post_".length()));
        } else if (buttonId.startsWith("approve_") || "Approve".equalsIgnoreCase(title)) {
            button = "approve";
        } else if (buttonId.startsWith("disapprove_") || "Disapprove".equalsIgnoreCase(title)) {
            button = "disapprove";
        } else {
            return Map.of("status", "ignored_interactive", "buttonId", buttonId, "wamid", wamid);
        }
        if (postId == null) {
            postId = posts.findLatestAwaitingApproval(from).orElse(null);
        }
        if (postId == null) {
            return Map.of("status", "error", "error", "no_awaiting_approval_post", "wamid", wamid);
        }
        Map<String, Object> result = approval.handleButton(from, postId, button, null, wamid);
        result.put("wamid", wamid);
        result.put("buttonId", buttonId);
        result.put("correlationId", correlationId);
        return result;
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

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static Optional<String> text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return Optional.empty();
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return Optional.empty();
        String s = v.asText();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}

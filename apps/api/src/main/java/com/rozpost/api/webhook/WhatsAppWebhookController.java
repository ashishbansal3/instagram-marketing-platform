package com.rozpost.api.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final String verifyToken;
    private final String metaMode;

    public WhatsAppWebhookController(
            ObjectMapper mapper,
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            @Value("${rozpost.webhook-verify-token:demo-verify}") String verifyToken,
            @Value("${rozpost.meta-mode:mock}") String metaMode
    ) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.redis = redis;
        this.verifyToken = verifyToken;
        this.metaMode = metaMode;
    }

    @GetMapping("/webhooks/whatsapp")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge == null ? "ok" : challenge);
        }
        return ResponseEntity.status(403).body("forbidden");
    }

    @PostMapping("/webhooks/whatsapp")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) throws Exception {
        // MVP skeleton: signature check is enforced in live mode only
        if ("live".equalsIgnoreCase(metaMode) && (signature == null || signature.isBlank())) {
            return ResponseEntity.status(401).body(Map.of("error", "missing signature"));
        }

        JsonNode root = mapper.readTree(rawBody);
        JsonNode message = root.path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        if (message.isMissingNode() || message.isNull()) {
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "no message"));
        }

        String wamid = text(message, "id").orElse(UUID.randomUUID().toString());
        String from = text(message, "from").orElse("unknown");
        String type = text(message, "type").orElse("unknown");
        String instructionText = null;
        String mediaId = null;

        if ("image".equals(type)) {
            instructionText = text(message.path("image"), "caption").orElse(null);
            mediaId = text(message.path("image"), "id").orElse(null);
        } else if ("interactive".equals(type)) {
            String buttonId = text(message.path("interactive").path("button_reply"), "id").orElse("");
            String decision = buttonId.startsWith("approve_") ? "approved"
                    : buttonId.startsWith("disapprove_") ? "disapproved" : "unknown";
            redis.opsForStream().add("approvals", Map.of(
                    "wamid", wamid,
                    "from", from,
                    "decision", decision,
                    "buttonId", buttonId
            ));
            return ResponseEntity.ok(Map.of("status", "queued_approval", "decision", decision, "wamid", wamid));
        }

        // Resolve tenant via demo client map; unknown senders stay orphaned for now
        UUID tenantId = jdbc.query(
                "SELECT tenant_id FROM clients WHERE wa_user_id = ? LIMIT 1",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                from
        );
        if (tenantId == null) {
            tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        }

        jdbc.update(
                """
                INSERT INTO jobs (tenant_id, type, status, idempotency_key, payload)
                VALUES (?::uuid, 'media_download', 'queued', ?, ?::jsonb)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                tenantId.toString(),
                wamid,
                mapper.createObjectNode()
                        .put("from", from)
                        .put("type", type)
                        .put("mediaId", mediaId)
                        .put("instruction_text", instructionText)
                        .toString()
        );

        redis.opsForStream().add("media-download", Map.of(
                "wamid", wamid,
                "from", from,
                "mediaId", mediaId == null ? "" : mediaId,
                "instruction_text", instructionText == null ? "" : instructionText,
                "tenant_id", tenantId.toString()
        ));

        log.info("RozPost WA ingest wamid={} type={} instructionPresent={}", wamid, type, instructionText != null);
        return ResponseEntity.ok(Map.of(
                "status", "queued_media_download",
                "wamid", wamid,
                "instruction_text", instructionText == null ? "" : instructionText
        ));
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return Optional.empty();
        String s = v.asText();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}

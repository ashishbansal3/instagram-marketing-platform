package com.rozpost.api.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.event.MediaProcessed;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.post.PostStatus;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Worker-media checkpoint callback: mark post awaiting_approval after durable preview assets exist.
 */
@RestController
@RequestMapping("/api/v1/internal")
public class AssetsReadyController {

    private static final Logger log = LoggerFactory.getLogger(AssetsReadyController.class);

    private final JdbcTemplate jdbc;
    private final DomainEventBus events;
    private final ObjectMapper mapper;
    private final RozpostProperties props;

    public AssetsReadyController(
            JdbcTemplate jdbc,
            DomainEventBus events,
            ObjectMapper mapper,
            RozpostProperties props
    ) {
        this.jdbc = jdbc;
        this.events = events;
        this.mapper = mapper;
        this.props = props;
    }

    @PostMapping("/posts/{postId}/assets-ready")
    public Map<String, Object> assetsReady(
            @PathVariable("postId") UUID postId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken
    ) {
        requireInternalAuth(internalToken);

        String correlationId = body.get("correlationId") == null
                ? CorrelationIdFilter.current()
                : String.valueOf(body.get("correlationId"));
        String instruction = body.get("instructionText") == null
                ? null
                : String.valueOf(body.get("instructionText"));
        String engine = body.get("engine") == null ? null : String.valueOf(body.get("engine"));

        Integer updated = jdbc.update(
                """
                UPDATE posts SET status = ?, updated_at = now(),
                  caption = COALESCE(?, caption)
                WHERE id = ?::uuid
                """,
                PostStatus.awaiting_approval.name(),
                instruction,
                postId.toString()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found: " + postId);
        }

        UUID tenantId = jdbc.query(
                "SELECT tenant_id FROM posts WHERE id = ?::uuid",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                postId.toString()
        );
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "post tenant missing");
        }

        UUID clientId = jdbc.query(
                "SELECT client_id FROM posts WHERE id = ?::uuid",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                postId.toString()
        );

        String previewJson;
        try {
            previewJson = mapper.writeValueAsString(body.getOrDefault("previewUrls", Map.of()));
        } catch (Exception e) {
            previewJson = "{}";
        }

        jdbc.update(
                """
                INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, ?)
                """,
                tenantId.toString(),
                clientId == null ? null : clientId.toString(),
                postId.toString(),
                "Preview ready (" + (engine == null ? "pipeline" : engine) + "). Reply Approve or Disapprove. previews=" + previewJson,
                "packages/meta-mocks/fixtures/whatsapp/outbound-approval-template.json"
        );

        events.publish(new MediaProcessed(
                tenantId,
                correlationId,
                postId,
                String.valueOf(body.getOrDefault("jobId", postId.toString())),
                Instant.now()
        ));

        log.info("assets_ready postId={} tenantId={} correlationId={}", postId, tenantId, correlationId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("postId", postId.toString());
        out.put("status", PostStatus.awaiting_approval.name());
        out.put("correlationId", correlationId);
        return out;
    }

    private void requireInternalAuth(String token) {
        String expected = props.getInternalApiToken();
        if (expected == null || expected.isBlank()) {
            // Local/dev: allow when token unset (compose may omit); live should set INTERNAL_API_TOKEN.
            return;
        }
        if (token == null || !constantTimeEquals(expected, token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}

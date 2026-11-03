package com.rozpost.api.interfaces.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.application.service.WhatsAppIngestService;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.infrastructure.meta.WhatsAppSignatureFilter;
import com.rozpost.api.infrastructure.meta.WhatsAppSignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Meta WhatsApp Cloud API webhooks.
 * Stable Meta console path + versioned /api/v1 path.
 * HMAC verified on raw body in {@link WhatsAppSignatureFilter} before JSON parse.
 */
@RestController
public class WhatsAppWebhookController {

    private final ObjectMapper mapper;
    private final WhatsAppIngestService ingestService;
    private final RozpostProperties props;
    private final WhatsAppSignatureVerifier signatures;

    public WhatsAppWebhookController(
            ObjectMapper mapper,
            WhatsAppIngestService ingestService,
            RozpostProperties props,
            WhatsAppSignatureVerifier signatures
    ) {
        this.mapper = mapper;
        this.ingestService = ingestService;
        this.props = props;
        this.signatures = signatures;
    }

    @GetMapping(
            value = {"/webhooks/whatsapp", "/api/v1/webhooks/whatsapp"},
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        String expected = props.getWebhookVerifyToken();
        if (expected == null || expected.isBlank()) {
            return ResponseEntity.status(503).body("verify token not configured");
        }
        if ("subscribe".equals(mode) && signatures.constantTimeEquals(expected, token)) {
            return ResponseEntity.ok(challenge == null ? "ok" : challenge);
        }
        return ResponseEntity.status(403).body("forbidden");
    }

    @PostMapping({"/webhooks/whatsapp", "/api/v1/webhooks/whatsapp"})
    public ResponseEntity<Map<String, Object>> ingest(
            HttpServletRequest request,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) throws Exception {
        byte[] raw = request instanceof WhatsAppSignatureFilter.CachedBodyRequest cached
                ? cached.getCachedBody()
                : request.getInputStream().readAllBytes();

        // Defense in depth if filter skipped (e.g. standalone tests).
        if (!signatures.verify(raw, signature)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_signature",
                    "code", "invalid_signature"
            ));
        }

        JsonNode root = mapper.readTree(new String(raw, StandardCharsets.UTF_8));
        Map<String, Object> result = ingestService.ingest(root);
        return ResponseEntity.ok(result);
    }
}

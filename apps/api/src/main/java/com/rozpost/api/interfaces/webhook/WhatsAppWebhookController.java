package com.rozpost.api.interfaces.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.application.service.WhatsAppIngestService;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WhatsAppWebhookController {

    private final ObjectMapper mapper;
    private final WhatsAppIngestService ingestService;
    private final RozpostProperties props;

    public WhatsAppWebhookController(
            ObjectMapper mapper,
            WhatsAppIngestService ingestService,
            RozpostProperties props
    ) {
        this.mapper = mapper;
        this.ingestService = ingestService;
        this.props = props;
    }

    @GetMapping("/webhooks/whatsapp")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        String expected = props.getWebhookVerifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank() && expected.equals(token)) {
            return ResponseEntity.ok(challenge == null ? "ok" : challenge);
        }
        return ResponseEntity.status(403).body("forbidden");
    }

    @PostMapping("/webhooks/whatsapp")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) throws Exception {
        if ("live".equalsIgnoreCase(props.getMetaMode()) && (signature == null || signature.isBlank())) {
            return ResponseEntity.status(401).body(Map.of("error", "missing signature"));
        }
        JsonNode root = mapper.readTree(rawBody);
        return ResponseEntity.ok(ingestService.ingest(root));
    }
}

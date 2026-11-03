package com.rozpost.api.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rozpost.api.application.service.WhatsAppIngestService;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Local / META_MODE=mock only — drives E2E without Meta. Disabled in live mode.
 */
@RestController
@RequestMapping("/api/v1/internal")
public class SimulateIntakeController {

    private final RozpostProperties props;
    private final WhatsAppIngestService ingest;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SimulateIntakeController(
            RozpostProperties props,
            WhatsAppIngestService ingest,
            JdbcTemplate jdbc,
            ObjectMapper mapper
    ) {
        this.props = props;
        this.ingest = ingest;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record SimulateRequest(UUID clientId, String instructionText) {}

    @PostMapping("/simulate-wa-message")
    public Map<String, Object> simulate(@RequestBody SimulateRequest req) throws Exception {
        if ("live".equalsIgnoreCase(props.getMetaMode())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "simulate-wa-message is available only when META_MODE=mock"
            );
        }
        if (req.clientId() == null) {
            throw new IllegalArgumentException("clientId required");
        }

        Map<String, Object> client = jdbc.query(
                """
                SELECT id, tenant_id, wa_user_id FROM clients WHERE id = ?::uuid
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return Map.<String, Object>of(
                            "id", rs.getObject("id").toString(),
                            "tenantId", rs.getObject("tenant_id").toString(),
                            "waUserId", rs.getString("wa_user_id")
                    );
                },
                req.clientId().toString()
        );
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown clientId");
        }

        ObjectNode root = (ObjectNode) loadFixture().deepCopy();
        String wamid = "wamid.SIM_" + UUID.randomUUID().toString().replace("-", "");
        rewrite(root, (String) client.get("waUserId"), wamid, req.instructionText());

        Map<String, Object> result = ingest.ingest(
                root,
                "whatsapp_mock",
                WhatsAppIngestService.FIXTURE_INBOUND
        );

        return Map.of(
                "ok", true,
                "metaMode", props.getMetaMode(),
                "clientId", client.get("id"),
                "tenantId", client.get("tenantId"),
                "waMessageId", wamid,
                "ingest", result
        );
    }

    private JsonNode loadFixture() throws IOException {
        ClassPathResource cpr = new ClassPathResource(
                "meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json");
        if (cpr.exists()) {
            return mapper.readTree(cpr.getInputStream());
        }
        Path repo = Path.of("packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json");
        if (Files.isRegularFile(repo)) {
            return mapper.readTree(Files.readString(repo));
        }
        Path fromApi = Path.of("../..", "packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json");
        if (Files.isRegularFile(fromApi)) {
            return mapper.readTree(Files.readString(fromApi));
        }
        // Minimal inline fixture so mock simulate works without file mount.
        return mapper.readTree("""
                {
                  "object":"whatsapp_business_account",
                  "entry":[{"changes":[{"value":{"messages":[{
                    "from":"919876543210",
                    "id":"wamid.PLACEHOLDER",
                    "type":"image",
                    "image":{"id":"mock-media","mime_type":"image/jpeg","caption":""}
                  }]}}]}]
                }
                """);
    }

    private void rewrite(ObjectNode root, String from, String wamid, String instruction) {
        for (JsonNode entry : root.withArray("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                if (!(value instanceof ObjectNode valueObj)) {
                    continue;
                }
                for (JsonNode message : valueObj.withArray("messages")) {
                    if (!(message instanceof ObjectNode msg)) {
                        continue;
                    }
                    msg.put("from", from);
                    msg.put("id", wamid);
                    msg.put("type", "image");
                    ObjectNode image = msg.has("image") && msg.get("image").isObject()
                            ? (ObjectNode) msg.get("image")
                            : mapper.createObjectNode();
                    if (!image.has("id")) {
                        image.put("id", "mock-media-" + UUID.randomUUID());
                    }
                    if (!image.has("mime_type")) {
                        image.put("mime_type", "image/jpeg");
                    }
                    if (instruction != null && !instruction.isBlank()) {
                        image.put("caption", instruction);
                    }
                    msg.set("image", image);
                }
            }
        }
    }
}

package com.rozpost.api.application.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.port.GbpClient;
import com.rozpost.api.domain.port.SiteStoragePort;
import com.rozpost.api.domain.port.WebsiteTemplateRenderer;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.infrastructure.gbp.DefaultGbpModeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WebsiteGbpSetupService {
    private static final Logger log = LoggerFactory.getLogger(WebsiteGbpSetupService.class);

    private final JdbcTemplate jdbc;
    private final WebsiteTemplateRenderer templates;
    private final SiteStoragePort storage;
    private final DefaultGbpModeFactory gbpFactory;
    private final ObjectMapper mapper;
    private final RozpostProperties props;
    private final boolean useLlm;

    public WebsiteGbpSetupService(
            JdbcTemplate jdbc,
            WebsiteTemplateRenderer templates,
            SiteStoragePort storage,
            DefaultGbpModeFactory gbpFactory,
            ObjectMapper mapper,
            RozpostProperties props,
            @Value("${rozpost.website.use-llm:false}") boolean useLlm) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.storage = storage;
        this.gbpFactory = gbpFactory;
        this.mapper = mapper;
        this.props = props;
        this.useLlm = useLlm;
    }

    @Transactional
    public Map<String, Object> run(UUID tenantId, UUID clientId, Map<String, Object> payload) {
        Map<String, Object> client = loadClient(clientId);
        String slug = stringVal(payload.get("tenantSlug"), stringVal(client.get("slug"), "business"));
        String businessName = stringVal(payload.get("businessName"), stringVal(client.get("business_name"), "Business"));
        String category = stringVal(payload.get("category"), stringVal(client.get("category"), null));
        String city = stringVal(payload.get("city"), stringVal(client.get("city"), null));
        String phone = stringVal(payload.get("phoneE164"), stringVal(client.get("phone_e164"), null));
        String bio = stringVal(payload.get("bio"), stringVal(client.get("bio"), null));
        String services = stringVal(payload.get("services"), stringVal(client.get("services"), null));
        String address = stringVal(payload.get("address"), stringVal(client.get("address"), null));
        String hoursJson = stringVal(payload.get("hoursJson"), hoursFromClient(client));
        Double lat = doubleVal(payload.get("latitude"), doubleObj(client.get("latitude")));
        Double lng = doubleVal(payload.get("longitude"), doubleObj(client.get("longitude")));

        upsertWebsitePending(tenantId, clientId, slug);

        String generationMode = useLlm ? "llm_flagged_unused" : "template";
        if (useLlm) {
            log.info("rozpost.website.use-llm=true but LLM path not wired — using template renderer");
        }

        String html =
                templates.render(
                        new WebsiteTemplateRenderer.BusinessSiteCopy(
                                businessName,
                                category,
                                city,
                                phone,
                                bio,
                                services,
                                address,
                                hoursJson,
                                slug));

        SiteStoragePort.StoredSite stored = storage.putHtml(tenantId.toString(), slug, html);
        String sha = sha256(html);
        jdbc.update(
                """
                UPDATE business_websites
                SET status = 'ready',
                    template_id = ?,
                    storage_backend = ?,
                    storage_key = ?,
                    preview_url = ?,
                    html_sha256 = ?,
                    generation_mode = ?,
                    error_message = NULL,
                    updated_at = now()
                WHERE client_id = ?::uuid
                """,
                templates.templateId(),
                stored.storageBackend(),
                stored.storageKey(),
                stored.previewUrl(),
                sha,
                generationMode,
                clientId.toString());

        GbpClient gbp = gbpFactory.client();
        GbpClient.GbpSetupResult gbpResult =
                gbp.setupProfile(
                        new GbpClient.GbpSetupRequest(
                                businessName,
                                category,
                                phone,
                                bio,
                                address,
                                city,
                                lat,
                                lng,
                                hoursJson,
                                stored.previewUrl()));

        upsertGbp(tenantId, clientId, stored.previewUrl(), businessName, category, phone, bio, address, city, lat, lng, hoursJson, gbpResult);

        jdbc.update(
                "UPDATE clients SET onboarding_status = 'environment_setup' WHERE id = ?::uuid AND onboarding_status = 'profile_collected'",
                clientId.toString());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clientId", clientId.toString());
        out.put("tenantId", tenantId.toString());
        out.put("websitePreviewUrl", stored.previewUrl());
        out.put("websiteStatus", "ready");
        out.put("gbpStatus", gbpResult.status());
        out.put("gbpMode", gbpResult.mode());
        out.put("generationMode", generationMode);
        out.put("metaMode", props.getMetaMode());
        log.info(
                "Website+GBP setup done clientId={} preview={} gbpStatus={}",
                clientId,
                stored.previewUrl(),
                gbpResult.status());
        return out;
    }

    private Map<String, Object> loadClient(UUID clientId) {
        return jdbc.query(
                """
                SELECT c.*, t.slug
                FROM clients c JOIN tenants t ON t.id = c.tenant_id
                WHERE c.id = ?::uuid
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Client not found: " + clientId);
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("business_name", rs.getString("business_name"));
                    m.put("category", rs.getString("category"));
                    m.put("city", rs.getString("city"));
                    m.put("phone_e164", rs.getString("phone_e164"));
                    m.put("bio", rs.getString("bio"));
                    m.put("services", rs.getString("services"));
                    m.put("address", rs.getString("address"));
                    m.put("hours_json", rs.getString("hours_json"));
                    m.put("latitude", rs.getObject("latitude"));
                    m.put("longitude", rs.getObject("longitude"));
                    m.put("slug", rs.getString("slug"));
                    return m;
                },
                clientId.toString());
    }

    private void upsertWebsitePending(UUID tenantId, UUID clientId, String slug) {
        jdbc.update(
                """
                INSERT INTO business_websites (tenant_id, client_id, slug, status)
                VALUES (?::uuid, ?::uuid, ?, 'generating')
                ON CONFLICT (client_id) DO UPDATE
                  SET status = 'generating', slug = EXCLUDED.slug, updated_at = now(), error_message = NULL
                """,
                tenantId.toString(),
                clientId.toString(),
                slug);
    }

    private void upsertGbp(
            UUID tenantId,
            UUID clientId,
            String websiteUrl,
            String businessName,
            String category,
            String phone,
            String bio,
            String address,
            String city,
            Double lat,
            Double lng,
            String hoursJson,
            GbpClient.GbpSetupResult result) {
        try {
            String setupJson = mapper.writeValueAsString(result.setupPayload());
            String checklistJson = mapper.writeValueAsString(result.verificationChecklist());
            String hours = hoursJson == null || hoursJson.isBlank() ? null : hoursJson;
            jdbc.update(
                    """
                    INSERT INTO gbp_profiles (
                      tenant_id, client_id, status, mode, google_location_name, website_url,
                      business_name, category, phone_e164, description, address, city,
                      latitude, longitude, hours_json, setup_payload, verification_checklist
                    ) VALUES (
                      ?::uuid, ?::uuid, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?,
                      ?, ?, ?::jsonb, ?::jsonb, ?::jsonb
                    )
                    ON CONFLICT (client_id) DO UPDATE SET
                      status = EXCLUDED.status,
                      mode = EXCLUDED.mode,
                      google_location_name = EXCLUDED.google_location_name,
                      website_url = EXCLUDED.website_url,
                      business_name = EXCLUDED.business_name,
                      category = EXCLUDED.category,
                      phone_e164 = EXCLUDED.phone_e164,
                      description = EXCLUDED.description,
                      address = EXCLUDED.address,
                      city = EXCLUDED.city,
                      latitude = EXCLUDED.latitude,
                      longitude = EXCLUDED.longitude,
                      hours_json = EXCLUDED.hours_json,
                      setup_payload = EXCLUDED.setup_payload,
                      verification_checklist = EXCLUDED.verification_checklist,
                      updated_at = now(),
                      error_message = NULL
                    """,
                    tenantId.toString(),
                    clientId.toString(),
                    result.status(),
                    result.mode(),
                    result.googleLocationName(),
                    websiteUrl,
                    businessName,
                    category,
                    phone,
                    bio,
                    address,
                    city,
                    lat,
                    lng,
                    hours,
                    setupJson,
                    checklistJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist gbp_profiles", e);
        }
    }

    public Map<String, Object> statusForClient(UUID clientId) {
        Map<String, Object> out = new LinkedHashMap<>();
        jdbc.query(
                "SELECT preview_url, status, generation_mode FROM business_websites WHERE client_id = ?::uuid",
                rs -> {
                    if (rs.next()) {
                        out.put("websitePreviewUrl", rs.getString("preview_url"));
                        out.put("websiteStatus", rs.getString("status"));
                        out.put("websiteGenerationMode", rs.getString("generation_mode"));
                    }
                },
                clientId.toString());
        jdbc.query(
                "SELECT status, mode, verification_checklist, website_url FROM gbp_profiles WHERE client_id = ?::uuid",
                rs -> {
                    if (rs.next()) {
                        out.put("gbpStatus", rs.getString("status"));
                        out.put("gbpMode", rs.getString("mode"));
                        out.put("gbpWebsiteUrl", rs.getString("website_url"));
                        try {
                            out.put(
                                    "gbpVerificationChecklist",
                                    mapper.readValue(rs.getString("verification_checklist"), Object.class));
                        } catch (Exception e) {
                            out.put("gbpVerificationChecklist", rs.getString("verification_checklist"));
                        }
                    }
                },
                clientId.toString());
        return out;
    }

    private static String hoursFromClient(Map<String, Object> client) {
        Object h = client.get("hours_json");
        return h == null ? null : h.toString();
    }

    private static String stringVal(Object primary, String fallback) {
        if (primary == null) {
            return fallback;
        }
        String s = primary.toString();
        return s.isBlank() ? fallback : s;
    }

    private static Double doubleVal(Object primary, Double fallback) {
        if (primary == null) {
            return fallback;
        }
        if (primary instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(primary.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Double doubleObj(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(String html) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(html.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }
}

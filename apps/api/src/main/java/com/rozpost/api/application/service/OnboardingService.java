package com.rozpost.api.application.service;

import com.rozpost.api.application.onboarding.WebsiteGbpSetupService;
import com.rozpost.api.domain.event.OnboardingCompleted;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.port.JobEnqueuePort;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OnboardingService {

    private final JdbcTemplate jdbc;
    private final DomainEventBus events;
    private final JobEnqueuePort jobs;
    private final WebsiteGbpSetupService websiteGbp;

    public OnboardingService(
            JdbcTemplate jdbc,
            DomainEventBus events,
            JobEnqueuePort jobs,
            WebsiteGbpSetupService websiteGbp) {
        this.jdbc = jdbc;
        this.events = events;
        this.jobs = jobs;
        this.websiteGbp = websiteGbp;
    }

    public record Command(
            String phoneE164,
            String businessName,
            String category,
            String city,
            String locale,
            String bio,
            String services,
            String address,
            String hoursJson,
            Double latitude,
            Double longitude
    ) {}

    @Transactional
    public Map<String, Object> onboard(Command cmd) {
        String phone = normalizePhone(cmd.phoneE164());
        Integer existing = jdbc.query(
                "SELECT 1 FROM clients WHERE phone_e164 = ? LIMIT 1",
                rs -> rs.next() ? 1 : null,
                phone
        );
        if (existing != null) {
            throw new IllegalStateException("Client already onboarded for phone");
        }

        String slug = uniqueSlug(cmd.businessName());
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String locale = cmd.locale() == null || cmd.locale().isBlank() ? "en-IN" : cmd.locale();
        String waUserId = phone.replace("+", "");
        String correlationId = CorrelationIdFilter.current();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "onboard-" + clientId;
        }

        jdbc.update(
                """
                INSERT INTO tenants (id, name, slug, status, plan, locale)
                VALUES (?::uuid, ?, ?, 'trial', 'free', ?)
                """,
                tenantId.toString(), cmd.businessName(), slug, locale
        );
        try {
            jdbc.update(
                    """
                    INSERT INTO clients
                      (id, tenant_id, business_name, category, city, phone_e164, wa_user_id,
                       onboarding_status, bio, services, address, hours_json, latitude, longitude)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, 'profile_collected', ?, ?, ?, ?::jsonb, ?, ?)
                    """,
                    clientId.toString(),
                    tenantId.toString(),
                    cmd.businessName(),
                    cmd.category(),
                    cmd.city(),
                    phone,
                    waUserId,
                    cmd.bio(),
                    cmd.services(),
                    cmd.address(),
                    blankToNullJson(cmd.hoursJson()),
                    cmd.latitude(),
                    cmd.longitude()
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Client already onboarded for phone");
        }

        // Mock-friendly IG connection row so publish quota/token lookups succeed locally.
        jdbc.update(
                """
                INSERT INTO meta_connections (tenant_id, provider, ig_user_id, access_token_enc, status)
                VALUES (?::uuid, 'instagram_login', ?, convert_to(?, 'UTF8'), 'active')
                ON CONFLICT (tenant_id, provider) DO NOTHING
                """,
                tenantId.toString(),
                "mock-ig-" + waUserId,
                "mock-token-" + waUserId
        );

        jdbc.update(
                """
                INSERT INTO business_websites (tenant_id, client_id, slug, status)
                VALUES (?::uuid, ?::uuid, ?, 'pending')
                ON CONFLICT (client_id) DO NOTHING
                """,
                tenantId.toString(),
                clientId.toString(),
                slug);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantSlug", slug);
        payload.put("businessName", cmd.businessName());
        payload.put("category", cmd.category());
        payload.put("city", cmd.city());
        payload.put("phoneE164", phone);
        payload.put("bio", cmd.bio());
        payload.put("services", cmd.services());
        payload.put("address", cmd.address());
        payload.put("hoursJson", cmd.hoursJson());
        payload.put("latitude", cmd.latitude());
        payload.put("longitude", cmd.longitude());
        payload.put("locale", locale);
        payload.put("correlationId", correlationId);

        UUID jobId =
                jobs.enqueue(
                        tenantId,
                        clientId,
                        "website_gbp_setup",
                        "website-gbp",
                        "onboarding:website-gbp:" + clientId,
                        payload);

        events.publish(
                new OnboardingCompleted(
                        tenantId,
                        clientId,
                        slug,
                        cmd.businessName(),
                        cmd.category(),
                        cmd.city(),
                        phone,
                        cmd.bio(),
                        cmd.services(),
                        cmd.address(),
                        cmd.hoursJson(),
                        cmd.latitude(),
                        cmd.longitude(),
                        correlationId,
                        Instant.now()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", clientId.toString());
        out.put("tenantId", tenantId.toString());
        out.put("clientId", clientId.toString());
        out.put("onboardingStatus", "profile_collected");
        out.put("status", "profile_collected");
        out.put("phoneE164", phone);
        out.put("phone", phone);
        out.put("waUserId", waUserId);
        out.put("tenantSlug", slug);
        out.put("businessName", cmd.businessName());
        out.put("category", cmd.category());
        out.put("city", cmd.city());
        out.put("bio", cmd.bio());
        out.put("websiteStatus", "pending");
        out.put("websitePreviewUrl", null);
        out.put("setupJobId", jobId.toString());
        out.put("correlationId", correlationId);
        return out;
    }

    public Map<String, Object> status(UUID clientId) {
        Map<String, Object> base = jdbc.query(
                """
                SELECT c.id, c.tenant_id, c.onboarding_status, c.business_name, c.phone_e164, c.wa_user_id,
                       c.category, c.city, c.bio, c.services, c.address, t.slug
                FROM clients c JOIN tenants t ON t.id = c.tenant_id
                WHERE c.id = ?::uuid
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Client not found");
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getObject("id").toString());
                    m.put("clientId", rs.getObject("id").toString());
                    m.put("tenantId", rs.getObject("tenant_id").toString());
                    m.put("onboardingStatus", rs.getString("onboarding_status"));
                    m.put("status", rs.getString("onboarding_status"));
                    m.put("businessName", rs.getString("business_name"));
                    m.put("phoneE164", rs.getString("phone_e164"));
                    m.put("phone", rs.getString("phone_e164"));
                    m.put("waUserId", rs.getString("wa_user_id"));
                    m.put("tenantSlug", rs.getString("slug"));
                    m.put("category", rs.getString("category"));
                    m.put("city", rs.getString("city"));
                    m.put("bio", rs.getString("bio"));
                    m.put("services", rs.getString("services"));
                    m.put("address", rs.getString("address"));
                    return m;
                },
                clientId.toString()
        );
        base.putAll(websiteGbp.statusForClient(clientId));
        return base;
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "business";
        }
        String candidate = base;
        int n = 0;
        while (Boolean.TRUE.equals(jdbc.query(
                "SELECT 1 FROM tenants WHERE slug = ? LIMIT 1",
                (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(),
                candidate
        ))) {
            n++;
            candidate = base + "-" + n;
        }
        return candidate;
    }

    static String normalizePhone(String phone) {
        String p = phone.trim().replace(" ", "").replace("-", "");
        if (!p.startsWith("+")) {
            if (p.matches("\\d{10}")) {
                p = "+91" + p;
            } else {
                p = "+" + p;
            }
        }
        return p;
    }

    private static String blankToNullJson(String hoursJson) {
        if (hoursJson == null || hoursJson.isBlank()) {
            return null;
        }
        String t = hoursJson.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) {
            // store free-text hours as a JSON string value
            return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return t;
    }
}

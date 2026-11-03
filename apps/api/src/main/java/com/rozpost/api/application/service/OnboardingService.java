package com.rozpost.api.application.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OnboardingService {

    private final JdbcTemplate jdbc;

    public OnboardingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Command(
            String phoneE164,
            String businessName,
            String category,
            String city,
            String locale
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
                      (id, tenant_id, business_name, category, city, phone_e164, wa_user_id, onboarding_status)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, 'profile_collected')
                    """,
                    clientId.toString(),
                    tenantId.toString(),
                    cmd.businessName(),
                    cmd.category(),
                    cmd.city(),
                    phone,
                    waUserId
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

        return Map.of(
                "tenantId", tenantId.toString(),
                "clientId", clientId.toString(),
                "onboardingStatus", "profile_collected",
                "phoneE164", phone,
                "waUserId", waUserId,
                "tenantSlug", slug
        );
    }

    public Map<String, Object> status(UUID clientId) {
        return jdbc.query(
                """
                SELECT c.id, c.tenant_id, c.onboarding_status, c.business_name, c.phone_e164, c.wa_user_id, t.slug
                FROM clients c JOIN tenants t ON t.id = c.tenant_id
                WHERE c.id = ?::uuid
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Client not found");
                    }
                    return Map.<String, Object>of(
                            "clientId", rs.getObject("id").toString(),
                            "tenantId", rs.getObject("tenant_id").toString(),
                            "onboardingStatus", rs.getString("onboarding_status"),
                            "businessName", rs.getString("business_name"),
                            "phoneE164", rs.getString("phone_e164"),
                            "waUserId", rs.getString("wa_user_id"),
                            "tenantSlug", rs.getString("slug")
                    );
                },
                clientId.toString()
        );
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
}

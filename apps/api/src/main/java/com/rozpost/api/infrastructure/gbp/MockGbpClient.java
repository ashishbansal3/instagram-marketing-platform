package com.rozpost.api.infrastructure.gbp;

import com.rozpost.api.domain.port.GbpClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full mock adapter for META_MODE≠live: creates a local setup payload + Google verification checklist
 * without calling Google APIs.
 */
@Component
public class MockGbpClient implements GbpClient {

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public GbpSetupResult setupProfile(GbpSetupRequest request) {
        String locationName =
                "accounts/mock-"
                        + UUID.randomUUID().toString().substring(0, 8)
                        + "/locations/"
                        + slugify(request.businessName());

        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("completeEnvironmentSetup", true);
        setup.put("websiteUrl", request.websiteUrl());
        setup.put("businessName", request.businessName());
        setup.put("category", request.category());
        setup.put("phoneE164", request.phoneE164());
        setup.put("description", request.description());
        setup.put("address", request.address());
        setup.put("city", request.city());
        setup.put("latitude", request.latitude());
        setup.put("longitude", request.longitude());
        setup.put("hoursJson", request.hoursJson());
        setup.put("note", "Mock GBP profile — no Google API call made");

        List<Map<String, Object>> checklist = new ArrayList<>();
        checklist.add(item("verify_ownership", "Verify ownership via postcard / phone / email in Google Business Profile", "required_in_live"));
        checklist.add(item("confirm_category", "Confirm primary category matches: " + nullToEmpty(request.category()), "required_in_live"));
        checklist.add(item("confirm_hours", "Confirm business hours in Google UI", "required_in_live"));
        checklist.add(item("confirm_phone", "Confirm phone " + nullToEmpty(request.phoneE164()), "required_in_live"));
        checklist.add(item("link_website", "Link website URL: " + nullToEmpty(request.websiteUrl()), "required_in_live"));
        checklist.add(item("confirm_location", "Confirm pin / address accuracy", "required_in_live"));
        checklist.add(item("add_photos", "Upload storefront / interior photos", "recommended"));

        return new GbpSetupResult("ready_mock", mode(), locationName, setup, checklist);
    }

    private static Map<String, Object> item(String code, String label, String severity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("severity", severity);
        m.put("status", "pending_owner_action");
        return m;
    }

    private static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "business";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

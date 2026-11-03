package com.rozpost.api.infrastructure.gbp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rozpost.api.domain.port.GbpClient;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live Google Business Profile adapter. Requires GBP_ACCESS_TOKEN + account.
 * When credentials are missing, returns a structured pending record with the same checklist
 * as mock (so onboarding still completes) rather than failing the whole job.
 */
@Component
public class LiveGbpClient implements GbpClient {
    private static final Logger log = LoggerFactory.getLogger(LiveGbpClient.class);

    private final RozpostProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String accessToken;
    private final String accountName;
    private final String apiBase;

    public LiveGbpClient(
            RozpostProperties props,
            ObjectMapper mapper,
            @Value("${rozpost.gbp.access-token:}") String accessToken,
            @Value("${rozpost.gbp.account-name:}") String accountName,
            @Value("${rozpost.gbp.api-base:https://mybusinessbusinessinformation.googleapis.com/v1}")
                    String apiBase) {
        this.props = props;
        this.mapper = mapper;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.accountName = accountName == null ? "" : accountName;
        this.apiBase = apiBase;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String mode() {
        return "live";
    }

    @Override
    public GbpSetupResult setupProfile(GbpSetupRequest request) {
        List<Map<String, Object>> checklist = liveChecklist(request);
        Map<String, Object> setup = baseSetup(request);

        if (accessToken.isBlank() || accountName.isBlank()) {
            log.warn("GBP live credentials missing — recording pending_credentials setup");
            setup.put("completeEnvironmentSetup", false);
            setup.put("blocker", "GBP_ACCESS_TOKEN / GBP_ACCOUNT_NAME not configured");
            return new GbpSetupResult("pending_credentials", mode(), null, setup, checklist);
        }

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("title", request.businessName());
            body.put("websiteUri", request.websiteUrl() == null ? "" : request.websiteUrl());
            if (request.phoneE164() != null && !request.phoneE164().isBlank()) {
                ObjectNode phone = body.putObject("phoneNumbers");
                phone.put("primaryPhone", request.phoneE164());
            }
            if (request.category() != null && !request.category().isBlank()) {
                ObjectNode cats = body.putObject("categories");
                ObjectNode primary = cats.putObject("primaryCategory");
                primary.put("displayName", request.category());
            }
            if (request.description() != null && !request.description().isBlank()) {
                ObjectNode profile = body.putObject("profile");
                profile.put("description", request.description());
            }
            if (request.latitude() != null && request.longitude() != null) {
                ObjectNode latlng = body.putObject("latlng");
                latlng.put("latitude", request.latitude());
                latlng.put("longitude", request.longitude());
            }

            String url = apiBase.replaceAll("/$", "") + "/" + accountName + "/locations";
            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                            .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            setup.put("httpStatus", response.statusCode());
            setup.put("apiResponseSnippet", truncate(response.body(), 500));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode node = mapper.readTree(response.body());
                String name = node.path("name").asText(null);
                setup.put("completeEnvironmentSetup", true);
                markChecklist(checklist, "api_create", "done");
                return new GbpSetupResult("created", mode(), name, setup, checklist);
            }

            setup.put("completeEnvironmentSetup", false);
            return new GbpSetupResult("api_error", mode(), null, setup, checklist);
        } catch (Exception e) {
            log.error("GBP live setup failed: {}", e.getMessage());
            setup.put("completeEnvironmentSetup", false);
            setup.put("error", e.getMessage());
            return new GbpSetupResult("api_error", mode(), null, setup, checklist);
        }
    }

    private Map<String, Object> baseSetup(GbpSetupRequest request) {
        Map<String, Object> setup = new LinkedHashMap<>();
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
        setup.put("metaMode", props.getMetaMode());
        return setup;
    }

    private static List<Map<String, Object>> liveChecklist(GbpSetupRequest request) {
        List<Map<String, Object>> checklist = new ArrayList<>();
        checklist.add(item("api_create", "Create location via Business Information API", "required"));
        checklist.add(item("verify_ownership", "Complete Google ownership verification", "required"));
        checklist.add(item("confirm_category", "Confirm category: " + nullToEmpty(request.category()), "required"));
        checklist.add(item("confirm_hours", "Confirm hours in Google Business Profile", "required"));
        checklist.add(item("confirm_phone", "Confirm phone " + nullToEmpty(request.phoneE164()), "required"));
        checklist.add(item("link_website", "Confirm website " + nullToEmpty(request.websiteUrl()), "required"));
        checklist.add(item("confirm_location", "Confirm address / lat-lng pin", "required"));
        return checklist;
    }

    private static void markChecklist(List<Map<String, Object>> checklist, String code, String status) {
        for (Map<String, Object> item : checklist) {
            if (code.equals(item.get("code"))) {
                item.put("status", status);
            }
        }
    }

    private static Map<String, Object> item(String code, String label, String severity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("severity", severity);
        m.put("status", "pending");
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

package com.rozpost.api.domain.port;

import java.util.List;
import java.util.Map;

/** Google Business Profile adapter (mock or live). */
public interface GbpClient {

    record GbpSetupRequest(
            String businessName,
            String category,
            String phoneE164,
            String description,
            String address,
            String city,
            Double latitude,
            Double longitude,
            String hoursJson,
            String websiteUrl
    ) {}

    record GbpSetupResult(
            String status,
            String mode,
            String googleLocationName,
            Map<String, Object> setupPayload,
            List<Map<String, Object>> verificationChecklist
    ) {}

    GbpSetupResult setupProfile(GbpSetupRequest request);

    String mode();
}

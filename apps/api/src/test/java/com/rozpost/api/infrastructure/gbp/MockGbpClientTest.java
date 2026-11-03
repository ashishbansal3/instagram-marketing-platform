package com.rozpost.api.infrastructure.gbp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rozpost.api.domain.port.GbpClient;
import org.junit.jupiter.api.Test;

class MockGbpClientTest {
    @Test
    void createsSetupPayloadAndChecklist() {
        MockGbpClient client = new MockGbpClient();
        GbpClient.GbpSetupResult result =
                client.setupProfile(
                        new GbpClient.GbpSetupRequest(
                                "Glow Studio",
                                "Beauty Salon",
                                "+919876543210",
                                "Bridal looks",
                                "Model Town",
                                "Ludhiana",
                                30.9,
                                75.8,
                                "Mon-Sat 10-7",
                                "http://localhost:8080/api/v1/sites/glow-studio"));

        assertEquals("mock", result.mode());
        assertEquals("ready_mock", result.status());
        assertTrue(result.googleLocationName().contains("locations/"));
        assertEquals(Boolean.TRUE, result.setupPayload().get("completeEnvironmentSetup"));
        assertFalse(result.verificationChecklist().isEmpty());
        assertTrue(
                result.verificationChecklist().stream()
                        .anyMatch(i -> "verify_ownership".equals(i.get("code"))));
    }
}

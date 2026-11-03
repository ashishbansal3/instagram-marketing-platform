package com.rozpost.api.interfaces.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.application.service.WhatsAppIngestService;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.infrastructure.meta.WhatsAppSignatureFilter;
import com.rozpost.api.infrastructure.meta.WhatsAppSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock WhatsAppIngestService ingest;

    RozpostProperties props = new RozpostProperties();
    WhatsAppSignatureVerifier signatures;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        props.setMetaMode("live");
        props.setMetaAppSecret("test-secret");
        props.setWebhookVerifyToken("verify-token");
        signatures = new WhatsAppSignatureVerifier(props);
        WhatsAppWebhookController controller =
                new WhatsAppWebhookController(new ObjectMapper(), ingest, props, signatures);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new WhatsAppSignatureFilter(props, signatures))
                .build();
    }

    @Test
    void liveModeRejectsMissingSignatureOnBothPaths() throws Exception {
        mvc.perform(post("/api/v1/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entry\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entry\":[]}"))
                .andExpect(status().isUnauthorized());
        verify(ingest, never()).ingest(any());
    }

    @Test
    void liveModeAcceptsValidHmacAndAcks() throws Exception {
        String body = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[]}}]}]}";
        String sig = "sha256=" + hmac(body, "test-secret");
        when(ingest.ingest(any())).thenReturn(Map.of("status", "ignored", "reason", "no_message"));

        mvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sig)
                        .content(body))
                .andExpect(status().isOk());
        verify(ingest).ingest(any());
    }

    @Test
    void hubVerifyUsesConstantTimeTokenCompare() throws Exception {
        mvc.perform(get("/api/v1/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify-token")
                        .param("hub.challenge", "challenge-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-123"));

        mvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "x"))
                .andExpect(status().isForbidden());
    }

    private static String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}

package com.rozpost.api.infrastructure.meta;

import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppSignatureVerifierTest {

    @Test
    void mockModeSkipsHmac() {
        RozpostProperties props = new RozpostProperties();
        props.setMetaMode("mock");
        assertTrue(new WhatsAppSignatureVerifier(props).verify("x".getBytes(StandardCharsets.UTF_8), null));
    }

    @Test
    void liveRequiresConfiguredSecretAndValidHmac() throws Exception {
        RozpostProperties props = new RozpostProperties();
        props.setMetaMode("live");
        props.setMetaAppSecret("");
        WhatsAppSignatureVerifier v = new WhatsAppSignatureVerifier(props);
        assertFalse(v.verify("{}".getBytes(StandardCharsets.UTF_8), "sha256=ab"));

        props.setMetaAppSecret("secret");
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        assertTrue(v.verify(body, sig));
        assertFalse(v.verify(body, "sha256=deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    }

    @Test
    void constantTimeEqualsRejectsLengthMismatch() {
        WhatsAppSignatureVerifier v = new WhatsAppSignatureVerifier(new RozpostProperties());
        assertFalse(v.constantTimeEquals("abc", "ab"));
        assertTrue(v.constantTimeEquals("token", "token"));
        assertFalse(v.constantTimeEquals("token", "tokem"));
    }
}

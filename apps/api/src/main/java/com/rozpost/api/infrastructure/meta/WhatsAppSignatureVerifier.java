package com.rozpost.api.infrastructure.meta;

import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * WhatsApp Cloud API HMAC (X-Hub-Signature-256) and hub.verify_token helpers.
 * Constant-time compares per production best practices §2.
 */
@Component
public class WhatsAppSignatureVerifier {

    private final RozpostProperties props;

    public WhatsAppSignatureVerifier(RozpostProperties props) {
        this.props = props;
    }

    /**
     * Live mode: require valid HMAC-SHA256 over raw body.
     * Mock mode: skip (fixtures / local smoke only).
     */
    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (isMock()) {
            return true;
        }
        if (props.getMetaAppSecret() == null || props.getMetaAppSecret().isBlank()) {
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String provided = signatureHeader.substring("sha256=".length()).trim().toLowerCase();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    props.getMetaAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            return constantTimeEquals(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    public boolean isMock() {
        return !"live".equalsIgnoreCase(props.getMetaMode());
    }
}

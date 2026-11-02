package com.rozpost.api.infrastructure.meta;

import com.rozpost.api.infrastructure.config.RozpostProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies X-Hub-Signature-256 over raw body BEFORE JSON parse (live mode only).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WhatsAppSignatureFilter extends OncePerRequestFilter {

    private final RozpostProperties props;

    public WhatsAppSignatureFilter(RozpostProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/webhooks/whatsapp".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        HttpServletRequest wrapped = new CachedBodyRequest(request, body);

        if ("live".equalsIgnoreCase(props.getMetaMode())) {
            String secret = props.getMetaAppSecret();
            String header = request.getHeader("X-Hub-Signature-256");
            if (secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"title\":\"Unauthorized\",\"detail\":\"missing or invalid signature\",\"status\":401}");
                return;
            }
            String expected = "sha256=" + hmacSha256Hex(secret, body);
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    header.trim().getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"title\":\"Unauthorized\",\"detail\":\"signature mismatch\",\"status\":401}");
                return;
            }
        }

        filterChain.doFilter(wrapped, response);
    }

    private static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {}
                @Override public int read() { return bais.read(); }
            };
        }
    }
}

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Reads raw body bytes and verifies X-Hub-Signature-256 BEFORE JSON parse (live mode).
 * Covers both Meta console path and versioned /api/v1 path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WhatsAppSignatureFilter extends OncePerRequestFilter {

    private static final Set<String> PATHS = Set.of(
            "/webhooks/whatsapp",
            "/api/v1/webhooks/whatsapp"
    );

    private final RozpostProperties props;
    private final WhatsAppSignatureVerifier verifier;

    public WhatsAppSignatureFilter(RozpostProperties props, WhatsAppSignatureVerifier verifier) {
        this.props = props;
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        CachedBodyRequest wrapped = new CachedBodyRequest(request, body);

        if ("live".equalsIgnoreCase(props.getMetaMode())) {
            String header = request.getHeader("X-Hub-Signature-256");
            if (!verifier.verify(body, header)) {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"title\":\"Unauthorized\",\"detail\":\"invalid WhatsApp signature\",\"status\":401,\"code\":\"invalid_signature\"}"
                );
                return;
            }
        }

        filterChain.doFilter(wrapped, response);
    }

    /** Exposes cached raw bytes for controllers that need them. */
    public static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        public byte[] getCachedBody() {
            return body;
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

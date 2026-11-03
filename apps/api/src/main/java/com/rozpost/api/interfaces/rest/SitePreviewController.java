package com.rozpost.api.interfaces.rest;

import com.rozpost.api.infrastructure.site.SiteStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/v1/sites")
public class SitePreviewController {

    private final JdbcTemplate jdbc;
    private final SiteStorageService storage;

    public SitePreviewController(JdbcTemplate jdbc, SiteStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @GetMapping(value = "/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable String slug) {
        return jdbc.query(
                """
                SELECT tenant_id::text, storage_backend, storage_key, preview_url, status
                FROM business_websites WHERE slug = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return ResponseEntity.notFound().build();
                    }
                    String status = rs.getString("status");
                    if (!"ready".equals(status)) {
                        return ResponseEntity.status(409)
                                .contentType(MediaType.TEXT_HTML)
                                .body("<!doctype html><html><body><p>Site is still generating…</p></body></html>");
                    }
                    String tenantId = rs.getString("tenant_id");
                    try {
                        var path = storage.localFile(tenantId, slug);
                        if (Files.isRegularFile(path)) {
                            String html = Files.readString(path, StandardCharsets.UTF_8);
                            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
                        }
                    } catch (Exception ignored) {
                        // fall through
                    }
                    String previewUrl = rs.getString("preview_url");
                    if (previewUrl != null && previewUrl.startsWith("http") && !previewUrl.contains("/api/v1/sites/")) {
                        return ResponseEntity.status(302).header("Location", previewUrl).build();
                    }
                    return ResponseEntity.status(404)
                            .contentType(MediaType.TEXT_HTML)
                            .body("<!doctype html><html><body><p>Site HTML not found on this host.</p></body></html>");
                },
                slug);
    }
}

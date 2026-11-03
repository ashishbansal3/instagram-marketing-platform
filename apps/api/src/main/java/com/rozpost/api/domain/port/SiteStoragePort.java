package com.rozpost.api.domain.port;

import java.nio.charset.StandardCharsets;

/** Persists generated business-site HTML/assets to S3 or local filesystem. */
public interface SiteStoragePort {

    record StoredSite(String storageBackend, String storageKey, String previewUrl) {}

    StoredSite putHtml(String tenantId, String slug, String html);

    default StoredSite putHtml(String tenantId, String slug, byte[] htmlBytes) {
        return putHtml(tenantId, slug, new String(htmlBytes, StandardCharsets.UTF_8));
    }

    /** Backend label: local | s3 */
    String backend();
}

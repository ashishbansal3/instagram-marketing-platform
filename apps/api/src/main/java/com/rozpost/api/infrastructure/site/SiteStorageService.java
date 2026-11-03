package com.rozpost.api.infrastructure.site;

import com.rozpost.api.domain.port.SiteStoragePort;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores generated sites on local disk when META_MODE=mock / APP_ENV=local / S3 unavailable;
 * otherwise uploads to S3 (MinIO-compatible).
 */
@Service
public class SiteStorageService implements SiteStoragePort {
    private static final Logger log = LoggerFactory.getLogger(SiteStorageService.class);

    private final RozpostProperties props;
    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;
    private final String apiPublicBaseUrl;
    private final Path localRoot;
    private boolean s3Ready;

    public SiteStorageService(
            RozpostProperties props,
            S3Client s3,
            @Value("${rozpost.s3.bucket:rozpost-media}") String bucket,
            @Value("${rozpost.s3.public-base-url:}") String publicBaseUrl,
            @Value("${rozpost.sites.local-root:./data/sites}") String localRoot,
            @Value("${rozpost.api-public-base-url:http://localhost:8080}") String apiPublicBaseUrl) {
        this.props = props;
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
        this.apiPublicBaseUrl = apiPublicBaseUrl;
        this.localRoot = Path.of(localRoot);
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(localRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create local site root " + localRoot, e);
        }
        if (forceLocal()) {
            s3Ready = false;
            log.info("Site storage: local filesystem ({})", localRoot.toAbsolutePath());
            return;
        }
        try {
            s3.headBucket(b -> b.bucket(bucket));
            s3Ready = true;
            log.info("Site storage: S3 bucket {}", bucket);
        } catch (Exception e) {
            s3Ready = false;
            log.warn("S3 unavailable for sites — local fallback: {}", e.getMessage());
        }
    }

    private boolean forceLocal() {
        return props.isMock()
                || "local".equalsIgnoreCase(props.getAppEnv())
                || bucket == null
                || bucket.isBlank();
    }

    @Override
    public String backend() {
        return s3Ready ? "s3" : "local";
    }

    @Override
    public StoredSite putHtml(String tenantId, String slug, String html) {
        String key = "sites/" + tenantId + "/" + slug + "/index.html";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        if (s3Ready) {
            try {
                s3.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType("text/html; charset=utf-8")
                                .build(),
                        RequestBody.fromBytes(bytes));
                String url =
                        publicBaseUrl.isBlank()
                                ? apiPreviewUrl(slug)
                                : publicBaseUrl.replaceAll("/$", "") + "/" + key;
                return new StoredSite("s3", key, url);
            } catch (Exception e) {
                log.warn("S3 put failed for site {}, local fallback: {}", key, e.getMessage());
            }
        }

        try {
            Path dest = localRoot.resolve(tenantId).resolve(slug).resolve("index.html");
            Files.createDirectories(dest.getParent());
            Files.write(dest, bytes);
            String storageKey = tenantId + "/" + slug + "/index.html";
            return new StoredSite("local", storageKey, apiPreviewUrl(slug));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist site HTML for " + slug, e);
        }
    }

    public Path localFile(String tenantId, String slug) {
        return localRoot.resolve(tenantId).resolve(slug).resolve("index.html");
    }

    private String apiPreviewUrl(String slug) {
        return apiPublicBaseUrl.replaceAll("/$", "") + "/api/v1/sites/" + slug;
    }
}

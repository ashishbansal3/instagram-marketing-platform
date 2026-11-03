package com.rozpost.worker.media.infrastructure.meta

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.media.domain.port.WhatsAppMediaFetcher
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Primary
@Component
class MetaModeWhatsAppMediaFetcher(
    private val mock: MockWhatsAppMediaFetcher,
    private val live: LiveWhatsAppMediaFetcher,
    @Value("\${rozpost.meta-mode:\${META_MODE:mock}}") private val metaMode: String,
) : WhatsAppMediaFetcher {
    override fun fetchToFile(waMediaId: String, destDir: Path, kind: String): Path =
        if ("live".equals(metaMode, ignoreCase = true)) {
            live.fetch(waMediaId, destDir, kind)
        } else {
            mock.fetch(waMediaId, destDir, kind)
        }
}

@Component
class MockWhatsAppMediaFetcher {
    private val log = LoggerFactory.getLogger(javaClass)

    fun fetch(waMediaId: String, destDir: Path, kind: String): Path {
        Files.createDirectories(destDir)
        val dest = destDir.resolve("wa-$waMediaId${if (kind == "video") ".mp4" else ".jpg"}")
        val configured = System.getenv("MOCK_MEDIA_PATH")
        val candidates = listOfNotNull(
            configured?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
            Path.of("/app/fixtures/sample-salon.jpg"),
            Path.of("fixtures/sample-salon.jpg"),
            Path.of("../fixtures/sample-salon.jpg"),
            Path.of("../../fixtures/sample-salon.jpg"),
            Path.of("packages/meta-mocks/fixtures/whatsapp/sample-inbound.jpg"),
        )
        val src = candidates.firstOrNull { Files.isRegularFile(it) }
        if (src != null) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
            log.info("mock WA media fetch waMediaId={} fixture={}", waMediaId, src)
            return dest
        }
        // Minimal JPEG SOI/EOI stub when fixtures are absent in the worker image.
        Files.write(dest, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        log.warn("mock WA media fetch used empty JPEG stub waMediaId={}", waMediaId)
        return dest
    }
}

@Component
class LiveWhatsAppMediaFetcher(
    private val props: MediaProperties,
    private val mapper: ObjectMapper,
    restClientBuilder: RestClient.Builder,
    @Value("\${rozpost.meta-wa-access-token:\${META_WA_ACCESS_TOKEN:}}") private val accessToken: String,
    @Value("\${rozpost.meta-graph-version:\${META_GRAPH_VERSION:v21.0}}") private val graphVersion: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = restClientBuilder.build()

    fun fetch(waMediaId: String, destDir: Path, kind: String): Path {
        if (accessToken.isBlank()) {
            error("META_WA_ACCESS_TOKEN required for live WA media download")
        }
        Files.createDirectories(destDir)
        val metaUrl = "https://graph.facebook.com/$graphVersion/$waMediaId"
        val metaBody = http.get()
            .uri(URI.create(metaUrl))
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(String::class.java)
            ?: error("empty Graph media metadata for $waMediaId")
        val url = mapper.readTree(metaBody).path("url").asText(null)
            ?: error("Graph media response missing url for $waMediaId")
        val bytes = http.get()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(ByteArray::class.java)
            ?: error("empty WA media bytes for $waMediaId")
        val ext = if (kind == "video") ".mp4" else ".jpg"
        val dest = destDir.resolve("wa-$waMediaId$ext")
        Files.write(dest, bytes)
        log.info(
            "live WA media downloaded waMediaId={} bytes={} bucketHint={}",
            waMediaId,
            bytes.size,
            props.s3.bucket,
        )
        return dest
    }
}

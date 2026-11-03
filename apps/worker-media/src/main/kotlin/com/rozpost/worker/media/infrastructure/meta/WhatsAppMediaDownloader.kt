package com.rozpost.worker.media.infrastructure.meta

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

/**
 * Resolves WhatsApp Cloud API media bytes.
 * META_MODE=mock: fixture / generated still (no live Graph).
 * META_MODE=live: Graph media metadata → binary download with access token.
 */
@Component
class WhatsAppMediaDownloader(
    private val props: MediaProperties,
    private val mapper: ObjectMapper,
    restClientBuilder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = restClientBuilder.build()

    fun downloadTo(waMediaId: String, dest: Path, kind: String = "image"): Path {
        Files.createDirectories(dest.parent)
        return if (props.whatsapp.metaMode.equals("live", ignoreCase = true)) {
            downloadLive(waMediaId, dest)
        } else {
            downloadMock(waMediaId, dest, kind)
        }
    }

    private fun downloadMock(waMediaId: String, dest: Path, kind: String): Path {
        val candidates = listOf(
            Path.of(props.whatsapp.mockMediaPath),
            Path.of("/app/fixtures/sample-salon.jpg"),
            Path.of("fixtures/sample-salon.jpg"),
            Path.of("../fixtures/sample-salon.jpg"),
            Path.of("../../fixtures/sample-salon.jpg"),
        )
        val fixture = candidates.firstOrNull { Files.isRegularFile(it) }
        if (fixture != null) {
            Files.copy(fixture, dest, StandardCopyOption.REPLACE_EXISTING)
            log.info("wa_mock_media_fixture waMediaId={} path={}", waMediaId, fixture)
            return dest
        }
        // Last-resort stub so META_MODE=mock E2E never depends on a missing mount.
        val img = BufferedImage(640, 960, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(245, 236, 220)
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color(30, 30, 30)
        g.drawString("mock WA media $waMediaId", 24, 64)
        g.drawString("kind=$kind", 24, 96)
        g.dispose()
        ImageIO.write(img, "jpg", dest.toFile())
        log.info("wa_mock_media_generated waMediaId={}", waMediaId)
        return dest
    }

    private fun downloadLive(waMediaId: String, dest: Path): Path {
        val token = props.whatsapp.accessToken
        require(token.isNotBlank()) { "META_WA_ACCESS_TOKEN required when META_MODE=live" }
        val metaUrl =
            "https://graph.facebook.com/${props.whatsapp.graphVersion}/$waMediaId?access_token=$token"
        val metaBody = http.get().uri(URI.create(metaUrl)).retrieve().body(String::class.java)
            ?: error("empty Graph media metadata for $waMediaId")
        val url = mapper.readTree(metaBody).path("url").asText(null)
            ?: error("Graph media metadata missing url for $waMediaId")
        val bytes = http.get()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .retrieve()
            .toEntity(ByteArray::class.java)
            .body
            ?: error("empty Graph media bytes for $waMediaId")
        Files.write(dest, bytes)
        log.info("wa_live_media_downloaded waMediaId={} bytes={}", waMediaId, bytes.size)
        return dest
    }
}

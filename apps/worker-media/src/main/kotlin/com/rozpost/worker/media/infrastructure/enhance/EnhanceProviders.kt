package com.rozpost.worker.media.infrastructure.enhance

import com.rozpost.worker.media.domain.error.ProviderUnavailableException
import com.rozpost.worker.media.domain.model.EnhanceResult
import com.rozpost.worker.media.domain.strategy.EnhanceStrategy
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import com.rozpost.worker.media.infrastructure.resilience.ProviderResilience
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@Component
@Order(10)
class PhotoroomEnhanceStrategy(
    private val props: MediaProperties,
    restClientBuilder: RestClient.Builder,
) : EnhanceStrategy {
    private val http = restClientBuilder.build()
    override val name = "photoroom"
    override val priority = 10
    override fun available(): Boolean = props.enhance.photoroomApiKey.isNotBlank()

    override fun enhance(
        input: Path,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): EnhanceResult {
        Files.createDirectories(workDir)
        val apiKey = props.enhance.photoroomApiKey
        return try {
            val bytes = Files.readAllBytes(input)
            val response = http.post()
                .uri("https://sdk.photoroom.com/v1/segment")
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/octet-stream")
                .header("X-Correlation-Id", correlationId ?: "")
                .body(bytes)
                .retrieve()
                .toEntity(ByteArray::class.java)
            val out = workDir.resolve("enhanced-photoroom.png")
            Files.write(out, response.body ?: error("empty Photoroom body"))
            EnhanceResult(out, name)
        } catch (ex: Exception) {
            throw ProviderUnavailableException(name, ex)
        }
    }
}

@Component
@Order(20)
class FalEnhanceStrategy(
    private val props: MediaProperties,
    restClientBuilder: RestClient.Builder,
) : EnhanceStrategy {
    private val http = restClientBuilder.build()
    override val name = "fal"
    override val priority = 20
    override fun available(): Boolean = props.enhance.falApiKey.isNotBlank()

    override fun enhance(
        input: Path,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): EnhanceResult {
        Files.createDirectories(workDir)
        val prompt = instructionText?.takeIf { it.isNotBlank() }
            ?: "Enhance photo for Instagram, keep identity faithful, clean background"
        return try {
            val payload = mapOf("image_url" to input.toUri().toString(), "prompt" to prompt)
            val response = http.post()
                .uri("https://fal.run/fal-ai/clarity-upscaler")
                .header("Authorization", "Key ${props.enhance.falApiKey}")
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", correlationId ?: "")
                .body(payload)
                .retrieve()
                .toEntity(Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val imageUrl = ((response.body?.get("image") as? Map<*, *>)?.get("url") as? String)
                ?: (response.body?.get("image_url") as? String)
                ?: error("fal response missing image url")
            val downloaded = http.get().uri(URI.create(imageUrl)).retrieve().toEntity(ByteArray::class.java)
            val out = workDir.resolve("enhanced-fal.png")
            Files.write(out, downloaded.body ?: error("empty fal image"))
            EnhanceResult(out, name)
        } catch (ex: Exception) {
            throw ProviderUnavailableException(name, ex)
        }
    }
}

/**
 * Offline / CI fallback. Registered only when media.enhance.allow-mock-fallback=true.
 * Not a product path — feature-flagged only.
 */
@Component
@Order(1000)
@ConditionalOnProperty(name = ["media.enhance.allow-mock-fallback"], havingValue = "true", matchIfMissing = true)
class MockEnhanceStrategy : EnhanceStrategy {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "mock"
    override val priority = 1000
    override fun available(): Boolean = true

    override fun enhance(
        input: Path,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): EnhanceResult {
        Files.createDirectories(workDir)
        log.info(
            "mock enhance active correlationId={} instructionPresent={}",
            correlationId ?: MDC.get("correlationId"),
            !instructionText.isNullOrBlank(),
        )
        val src = ImageIO.read(input.toFile()) ?: error("Cannot read image $input")
        var img = ensureRgb(src)
        img = adjustContrast(img, 1.12f)
        img = sharpen(img)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val barH = (img.height * 0.12).toInt().coerceAtLeast(64)
        g.color = Color(0, 0, 0, 160)
        g.fillRect(0, img.height - barH, img.width, barH)
        val title = brandName?.takeIf { it.isNotBlank() } ?: "RozPost"
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, (barH * 0.35).toInt().coerceAtLeast(22))
        g.drawString(title, 24, img.height - barH + (barH * 0.45).toInt())
        val note = instructionText?.takeIf { it.isNotBlank() }?.take(72)
            ?: "mock enhance · sharpness + overlay"
        g.font = Font("SansSerif", Font.PLAIN, (barH * 0.22).toInt().coerceAtLeast(14))
        g.color = Color(230, 230, 230)
        g.drawString(note, 24, img.height - barH + (barH * 0.78).toInt())
        g.dispose()
        val out = workDir.resolve("enhanced-mock.jpg")
        ImageIO.write(img, "jpg", out.toFile())
        return EnhanceResult(out, name)
    }

    private fun ensureRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB) return src
        val rgb = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.drawImage(src, 0, 0, Color.WHITE, null)
        g.dispose()
        return rgb
    }

    private fun adjustContrast(src: BufferedImage, factor: Float): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val rgb = src.getRGB(x, y)
                fun ch(v: Int) = (((v - 128) * factor) + 128).toInt().coerceIn(0, 255)
                val r = ch((rgb shr 16) and 0xff)
                val g = ch((rgb shr 8) and 0xff)
                val b = ch(rgb and 0xff)
                out.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return out
    }

    private fun sharpen(src: BufferedImage): BufferedImage {
        val kernel = Kernel(
            3, 3,
            floatArrayOf(0f, -0.25f, 0f, -0.25f, 2f, -0.25f, 0f, -0.25f, 0f),
        )
        return ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(src, null)
    }
}

/**
 * Registry selects the first available strategy by priority; wraps each call in a circuit breaker
 * and falls through on failure (Open/Closed for new providers — just register another @Component).
 */
@Component
class EnhanceStrategyRegistry(
    strategies: List<EnhanceStrategy>,
    private val resilience: ProviderResilience,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ordered = strategies.sortedBy { it.priority }

    fun enhance(
        input: Path,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): EnhanceResult {
        val candidates = ordered.filter { it.available() }
        require(candidates.isNotEmpty()) { "No enhance strategies available" }
        var last: Exception? = null
        for (strategy in candidates) {
            try {
                // Per-backend CB/retry/bulkhead; fall through ladder on failure.
                return resilience.call(strategy.name) {
                    strategy.enhance(input, instructionText, brandName, workDir, correlationId)
                }
            } catch (ex: Exception) {
                last = ex
                log.warn(
                    "enhance provider={} failed correlationId={} tenantId={} jobId={} err={}",
                    strategy.name,
                    correlationId,
                    MDC.get("tenantId"),
                    MDC.get("jobId"),
                    ex.message,
                )
            }
        }
        throw last ?: IllegalStateException("All enhance providers failed")
    }
}

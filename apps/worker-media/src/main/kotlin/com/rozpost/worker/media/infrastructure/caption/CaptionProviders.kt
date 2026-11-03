package com.rozpost.worker.media.infrastructure.caption

import com.rozpost.worker.media.domain.error.ProviderUnavailableException
import com.rozpost.worker.media.domain.model.CaptionResult
import com.rozpost.worker.media.domain.strategy.CaptionStrategy
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import com.rozpost.worker.media.infrastructure.resilience.ProviderResilience
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
@Order(10)
class RemotionCaptionStrategy(
    private val props: MediaProperties,
    restClientBuilder: RestClient.Builder,
) : CaptionStrategy {
    private val http = restClientBuilder.build()
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "remotion"
    override val priority = 10
    override fun available(): Boolean = props.remotion.baseUrl.isNotBlank()

    override fun assemble(
        videoOrImage: Path,
        caption: String,
        locale: String,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): CaptionResult {
        Files.createDirectories(workDir)
        val out = workDir.resolve("reel-captioned.mp4")
        try {
            val bodyBuilder = MultipartBodyBuilder()
            bodyBuilder.part("media", FileSystemResource(videoOrImage)).contentType(
                if (videoOrImage.fileName.toString().endsWith(".mp4")) MediaType.valueOf("video/mp4")
                else MediaType.IMAGE_JPEG,
            )
            bodyBuilder.part("caption", caption)
            bodyBuilder.part("locale", locale)
            bodyBuilder.part("instructionText", instructionText ?: "")
            bodyBuilder.part("brandName", brandName ?: "RozPost")
            bodyBuilder.part("correlationId", correlationId ?: "")

            val response = http.post()
                .uri("${props.remotion.baseUrl.trimEnd('/')}/v1/render")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("X-Correlation-Id", correlationId ?: "")
                .body(bodyBuilder.build())
                .retrieve()
                .toEntity(ByteArray::class.java)

            Files.write(out, response.body ?: error("empty remotion body"))
            val preview = response.headers.getFirst("X-Preview-Url")
            log.info("remotion caption ok correlationId={}", correlationId)
            return CaptionResult(out, name, preview)
        } catch (ex: Exception) {
            // Try legacy /render path once for older remotion builds
            try {
                val bodyBuilder = MultipartBodyBuilder()
                bodyBuilder.part("media", FileSystemResource(videoOrImage))
                bodyBuilder.part("caption", caption)
                bodyBuilder.part("locale", locale)
                bodyBuilder.part("instructionText", instructionText ?: "")
                bodyBuilder.part("brandName", brandName ?: "RozPost")
                val response = http.post()
                    .uri("${props.remotion.baseUrl.trimEnd('/')}/render")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-Correlation-Id", correlationId ?: "")
                    .body(bodyBuilder.build())
                    .retrieve()
                    .toEntity(ByteArray::class.java)
                Files.write(out, response.body ?: error("empty remotion body"))
                return CaptionResult(out, name, response.headers.getFirst("X-Preview-Url"))
            } catch (inner: Exception) {
                throw ProviderUnavailableException(name, inner)
            }
        }
    }
}

@Component
@Order(100)
class FfmpegCaptionFallbackStrategy : CaptionStrategy {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "ffmpeg-caption"
    override val priority = 100
    override fun available(): Boolean = true

    override fun assemble(
        videoOrImage: Path,
        caption: String,
        locale: String,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): CaptionResult {
        Files.createDirectories(workDir)
        val out = workDir.resolve("reel-captioned.mp4")
        val safeCaption = caption.replace(":", "\\:").replace("'", "")
        val label = (brandName ?: "RozPost").replace(":", "\\:").replace("'", "")
        val draw =
            "drawtext=text='$label':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=h-220:box=1:boxcolor=black@0.55:boxborderw=16," +
                "drawtext=text='$safeCaption':fontsize=36:fontcolor=white:x=(w-text_w)/2:y=h-140:box=1:boxcolor=black@0.45:boxborderw=12"
        val cmd = listOf(
            "ffmpeg", "-y", "-i", videoOrImage.toAbsolutePath().toString(),
            "-vf", draw, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an",
            out.toAbsolutePath().toString(),
        )
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val logText = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0 || !Files.exists(out)) {
            log.warn("ffmpeg caption failed correlationId={} err={}", correlationId, logText.takeLast(400))
            Files.copy(videoOrImage, out, StandardCopyOption.REPLACE_EXISTING)
        }
        return CaptionResult(out, name)
    }
}

@Component
class CaptionStrategyRegistry(
    strategies: List<CaptionStrategy>,
    private val resilience: ProviderResilience,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ordered = strategies.sortedBy { it.priority }

    fun assemble(
        videoOrImage: Path,
        caption: String,
        locale: String,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): CaptionResult {
        val candidates = ordered.filter { it.available() }
        var last: Exception? = null
        for (strategy in candidates) {
            try {
                return resilience.call(strategy.name) {
                    strategy.assemble(
                        videoOrImage, caption, locale, instructionText, brandName, workDir, correlationId,
                    )
                }
            } catch (ex: Exception) {
                last = ex
                log.warn(
                    "caption provider={} failed correlationId={} tenantId={} jobId={} err={}",
                    strategy.name, correlationId, MDC.get("tenantId"), MDC.get("jobId"), ex.message,
                )
            }
        }
        throw last ?: IllegalStateException("All caption providers failed")
    }
}

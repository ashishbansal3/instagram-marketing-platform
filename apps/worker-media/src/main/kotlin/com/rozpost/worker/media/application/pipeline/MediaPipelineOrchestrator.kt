package com.rozpost.worker.media.application.pipeline

import com.rozpost.worker.media.domain.event.DomainEventPublisher
import com.rozpost.worker.media.domain.event.MediaProcessed
import com.rozpost.worker.media.domain.model.ImageEnhanceJob
import com.rozpost.worker.media.domain.model.MediaDownloadJob
import com.rozpost.worker.media.domain.model.PipelineResult
import com.rozpost.worker.media.domain.model.ReelRenderJob
import com.rozpost.worker.media.domain.strategy.CutStrategy
import com.rozpost.worker.media.infrastructure.caption.CaptionStrategyRegistry
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import com.rozpost.worker.media.infrastructure.enhance.EnhanceStrategyRegistry
import com.rozpost.worker.media.infrastructure.ledger.CostLedgerService
import com.rozpost.worker.media.infrastructure.meta.WhatsAppMediaDownloader
import com.rozpost.worker.media.infrastructure.notify.ApprovalNotifyService
import com.rozpost.worker.media.infrastructure.queue.JobQueue
import com.rozpost.worker.media.infrastructure.storage.ObjectStorageService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.awt.Color
import java.awt.image.BufferedImage
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

@Service
class MediaPipelineOrchestrator(
    private val props: MediaProperties,
    private val storage: ObjectStorageService,
    private val enhanceRegistry: EnhanceStrategyRegistry,
    private val cutStrategy: CutStrategy,
    private val captionRegistry: CaptionStrategyRegistry,
    private val notify: ApprovalNotifyService,
    private val queue: JobQueue,
    private val events: DomainEventPublisher,
    private val costLedger: CostLedgerService,
    private val waMedia: WhatsAppMediaDownloader,
    restClientBuilder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = restClientBuilder.build()

    fun handleDownload(job: MediaDownloadJob): PipelineResult = withCorrelation(job.correlationId ?: job.jobId) {
        val work = workDir(job.tenantId, job.jobId)
        val ext = guessExt(job.sourceUrl ?: job.localPath, job.kind)
        val local = work.resolve("original$ext")
        when {
            !job.localPath.isNullOrBlank() && Files.isRegularFile(Path.of(job.localPath)) ->
                Files.copy(Path.of(job.localPath), local, StandardCopyOption.REPLACE_EXISTING)
            !job.sourceUrl.isNullOrBlank() -> {
                val body = http.get().uri(URI.create(job.sourceUrl)).retrieve().toEntity(ByteArray::class.java).body
                    ?: error("empty download from ${job.sourceUrl}")
                Files.write(local, body)
            }
            !job.waMediaId.isNullOrBlank() -> {
                // Official path: resolve WA Cloud media (Graph live, fixture stub when META_MODE=mock).
                waMedia.downloadTo(job.waMediaId, local, job.kind)
                log.info(
                    "wa_media_downloaded jobId={} postId={} waMediaId={} metaMode={}",
                    job.jobId, job.postId, job.waMediaId, props.whatsapp.metaMode,
                )
            }
            props.enhance.allowMockFallback && !props.whatsapp.metaMode.equals("live", ignoreCase = true) -> {
                writeMinimalJpeg(local)
                log.warn("mock download without waMediaId jobId={} postId={}", job.jobId, job.postId)
            }
            else -> error("MediaDownloadJob requires sourceUrl, localPath, or waMediaId")
        }
        val key = "${job.tenantId}/${job.postId}/original$ext"
        val url = storage.putFile(key, local, contentType(ext))
        if (job.kind == "image" || ext in listOf(".jpg", ".jpeg", ".png", ".webp")) {
            queue.enqueueEnhance(
                ImageEnhanceJob(
                    jobId = job.jobId,
                    tenantId = job.tenantId,
                    postId = job.postId,
                    assetKey = key,
                    instructionText = job.instructionText,
                    locale = job.locale,
                    correlationId = correlation(),
                ),
            )
        } else {
            queue.enqueueReel(
                ReelRenderJob(
                    jobId = job.jobId,
                    tenantId = job.tenantId,
                    postId = job.postId,
                    assetKey = key,
                    instructionText = job.instructionText,
                    locale = job.locale,
                    correlationId = correlation(),
                ),
            )
        }
        PipelineResult(
            jobId = job.jobId,
            tenantId = job.tenantId,
            postId = job.postId,
            status = "downloaded",
            correlationId = correlation(),
            originalKey = key,
            previewUrls = mapOf("original" to url),
            instructionText = job.instructionText,
        )
    }

    fun handleEnhance(job: ImageEnhanceJob): PipelineResult = withCorrelation(job.correlationId ?: job.jobId) {
        val work = workDir(job.tenantId, job.jobId)
        val input = work.resolve("enhance-input")
        storage.downloadTo(job.assetKey, input)
        val outcome = enhanceRegistry.enhance(
            input, job.instructionText, job.brandName, work.resolve("enhance"), correlation(),
        )
        val ext = if (outcome.outputPath.fileName.toString().endsWith(".png")) ".png" else ".jpg"
        val key = "${job.tenantId}/${job.postId}/enhanced$ext"
        val url = storage.putFile(key, outcome.outputPath, contentType(ext))
        costLedger.record(
            tenantId = job.tenantId,
            jobId = job.jobId,
            category = "image_api",
            amountInr = if (outcome.provider == "mock") BigDecimal("0.00") else BigDecimal("8.00"),
            meta = mapOf("provider" to outcome.provider, "postId" to job.postId),
        )
        queue.enqueueReel(
            ReelRenderJob(
                jobId = job.jobId,
                tenantId = job.tenantId,
                postId = job.postId,
                assetKey = key,
                instructionText = job.instructionText,
                caption = captionFrom(job.instructionText, job.locale),
                locale = job.locale,
                brandName = job.brandName,
                correlationId = correlation(),
            ),
        )
        PipelineResult(
            jobId = job.jobId,
            tenantId = job.tenantId,
            postId = job.postId,
            status = "enhanced",
            correlationId = correlation(),
            enhancedKey = key,
            previewUrls = mapOf("enhanced" to url),
            engine = outcome.provider,
            instructionText = job.instructionText,
        )
    }

    fun handleReel(job: ReelRenderJob): PipelineResult = withCorrelation(job.correlationId ?: job.jobId) {
        val work = workDir(job.tenantId, job.jobId)
        val input = work.resolve("reel-input")
        storage.downloadTo(job.assetKey, input)
        val reframed = work.resolve("reel-916.mp4")
        cutStrategy.reframeTo916(input, reframed, job.durationSeconds, correlation())
        val caption = job.caption ?: captionFrom(job.instructionText, job.locale)
        val captioned = captionRegistry.assemble(
            reframed, caption, job.locale, job.instructionText, job.brandName,
            work.resolve("caption"), correlation(),
        )
        val reelKey = "${job.tenantId}/${job.postId}/reel.mp4"
        val reelUrl = storage.putFile(reelKey, captioned.outputPath, "video/mp4")
        costLedger.record(
            tenantId = job.tenantId,
            jobId = job.jobId,
            category = "reel_render",
            amountInr = BigDecimal("15.00"),
            meta = mapOf("provider" to captioned.provider, "postId" to job.postId),
        )
        val previews = buildMap {
            put("reel", reelUrl)
            put("still", storage.publicUrl(job.assetKey))
            captioned.previewUrl?.let { put("captionPreview", it) }
        }
        val result = PipelineResult(
            jobId = job.jobId,
            tenantId = job.tenantId,
            postId = job.postId,
            status = "awaiting_approval",
            correlationId = correlation(),
            enhancedKey = job.assetKey,
            reelKey = reelKey,
            previewUrls = previews,
            engine = captioned.provider,
            instructionText = job.instructionText,
            message = "Asset ready for Approve / Disapprove",
        )
        events.publish(
            MediaProcessed(
                eventId = UUID.randomUUID().toString(),
                correlationId = result.correlationId,
                tenantId = result.tenantId,
                postId = result.postId,
                jobId = result.jobId,
                status = result.status,
                engine = result.engine,
                previewUrls = result.previewUrls,
                instructionText = result.instructionText,
            ),
        )
        notify.assetReadyForApproval(result)
        result
    }

    /** Synchronous process path for local/smoke fixtures (not a product "demo" surface). */
    fun processImageSync(
        fixture: Path,
        instructionText: String?,
        locale: String,
        brandName: String?,
        tenantId: String,
        correlationId: String? = null,
    ): PipelineResult = withCorrelation(correlationId ?: UUID.randomUUID().toString()) {
        val jobId = UUID.randomUUID().toString()
        val postId = "post-${jobId.take(8)}"
        val work = workDir(tenantId, jobId)
        val original = work.resolve("original.jpg")
        Files.copy(fixture, original, StandardCopyOption.REPLACE_EXISTING)
        val originalKey = "$tenantId/$postId/original.jpg"
        val originalUrl = storage.putFile(originalKey, original, "image/jpeg")

        val enhance = enhanceRegistry.enhance(
            original, instructionText, brandName, work.resolve("enhance"), correlation(),
        )
        val enhancedKey = if (enhance.outputPath.fileName.toString().endsWith(".png")) {
            val key = "$tenantId/$postId/enhanced.png"
            storage.putFile(key, enhance.outputPath, "image/png")
            key
        } else {
            val key = "$tenantId/$postId/enhanced.jpg"
            storage.putFile(key, enhance.outputPath, "image/jpeg")
            key
        }
        val enhancedUrl = storage.publicUrl(enhancedKey)
        costLedger.record(
            tenantId = tenantId,
            jobId = jobId,
            category = "image_api",
            amountInr = if (enhance.provider == "mock") BigDecimal("0.00") else BigDecimal("8.00"),
            meta = mapOf("provider" to enhance.provider, "postId" to postId),
        )

        val reframed = work.resolve("reel-916.mp4")
        cutStrategy.reframeTo916(enhance.outputPath, reframed, 5, correlation())
        val caption = captionFrom(instructionText, locale)
        val captioned = captionRegistry.assemble(
            reframed, caption, locale, instructionText, brandName, work.resolve("caption"), correlation(),
        )
        val reelKey = "$tenantId/$postId/reel.mp4"
        val reelUrl = storage.putFile(reelKey, captioned.outputPath, "video/mp4")
        costLedger.record(
            tenantId = tenantId,
            jobId = jobId,
            category = "reel_render",
            amountInr = BigDecimal("15.00"),
            meta = mapOf("provider" to captioned.provider, "postId" to postId),
        )

        val result = PipelineResult(
            jobId = jobId,
            tenantId = tenantId,
            postId = postId,
            status = "awaiting_approval",
            correlationId = correlation(),
            originalKey = originalKey,
            enhancedKey = enhancedKey,
            reelKey = reelKey,
            previewUrls = mapOf(
                "original" to originalUrl,
                "enhanced" to enhancedUrl,
                "reel" to reelUrl,
            ),
            engine = enhance.provider,
            instructionText = instructionText,
            message = "Asset ready for Approve / Disapprove",
        )
        events.publish(
            MediaProcessed(
                eventId = UUID.randomUUID().toString(),
                correlationId = result.correlationId,
                tenantId = result.tenantId,
                postId = result.postId,
                jobId = result.jobId,
                status = result.status,
                engine = result.engine,
                previewUrls = result.previewUrls,
                instructionText = result.instructionText,
            ),
        )
        notify.assetReadyForApproval(result)
        log.info("processImageSync complete correlationId={} engine={}", result.correlationId, result.engine)
        result
    }

    private fun captionFrom(instructionText: String?, locale: String): String {
        if (!instructionText.isNullOrBlank()) return instructionText.take(80)
        return when {
            locale.startsWith("pa") -> "ਸਾਡੀ ਨਵੀਂ ਆਫਰ — ਅੱਜ ਹੀ ਬੁੱਕ ਕਰੋ"
            locale.startsWith("hi") -> "नई ऑफर — आज ही बुक करें"
            else -> "New offer — book today"
        }
    }

    private fun workDir(tenantId: String, jobId: String): Path {
        val p = Path.of(props.workDir, tenantId, jobId)
        Files.createDirectories(p)
        return p
    }

    private fun guessExt(ref: String?, kind: String): String {
        val lower = ref?.lowercase() ?: ""
        return when {
            lower.contains(".png") -> ".png"
            lower.contains(".webp") -> ".webp"
            lower.contains(".mp4") -> ".mp4"
            lower.contains(".mov") -> ".mov"
            kind == "video" -> ".mp4"
            else -> ".jpg"
        }
    }

    private fun contentType(ext: String): String = when (ext.lowercase()) {
        ".png" -> "image/png"
        ".webp" -> "image/webp"
        ".mp4" -> "video/mp4"
        ".mov" -> "video/quicktime"
        else -> "image/jpeg"
    }

    private fun writeMinimalJpeg(dest: Path) {
        val img = BufferedImage(640, 960, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(245, 236, 220)
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color(40, 40, 40)
        g.drawString("RozPost mock still", 40, 80)
        g.dispose()
        Files.createDirectories(dest.parent)
        ImageIO.write(img, "jpg", dest.toFile())
    }

    private fun correlation(): String? = MDC.get("correlationId")

    private fun <T> withCorrelation(id: String, block: () -> T): T {
        val previous = MDC.get("correlationId")
        MDC.put("correlationId", id)
        return try {
            block()
        } finally {
            if (previous != null) MDC.put("correlationId", previous) else MDC.remove("correlationId")
        }
    }
}

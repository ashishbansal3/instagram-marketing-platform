package com.rozpost.worker.media.interfaces.rest

import com.rozpost.worker.media.application.pipeline.MediaPipelineOrchestrator
import com.rozpost.worker.media.domain.model.ImageEnhanceJob
import com.rozpost.worker.media.domain.model.MediaDownloadJob
import com.rozpost.worker.media.domain.model.PipelineResult
import com.rozpost.worker.media.domain.model.ReelRenderJob
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import com.rozpost.worker.media.infrastructure.notify.ApprovalNotifyService
import com.rozpost.worker.media.infrastructure.queue.JobQueue
import com.rozpost.worker.media.infrastructure.storage.ObjectStorageService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
class MediaController(
    private val pipeline: MediaPipelineOrchestrator,
    private val queue: JobQueue,
    private val props: MediaProperties,
    private val storage: ObjectStorageService,
    private val notify: ApprovalNotifyService,
) {

    /**
     * Process an uploaded still through enhance → cut → caption.
     * Production smoke / operator tooling — not a "demo" product surface.
     */
    @PostMapping("/process", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun process(
        @RequestParam("file", required = false) file: MultipartFile?,
        @RequestParam("fixture", required = false) fixtureName: String?,
        @RequestParam("instruction_text", required = false) instructionText: String?,
        @RequestParam("locale", defaultValue = "hi-IN") locale: String,
        @RequestParam("brand_name", required = false) brandName: String?,
        @RequestParam("tenant_id", defaultValue = "tenant-local") tenantId: String,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationHeader: String?,
    ): PipelineResult {
        val tmp = Files.createTempFile("media-input-", ".jpg")
        when {
            file != null && !file.isEmpty -> file.transferTo(tmp)
            !fixtureName.isNullOrBlank() -> {
                val candidates = listOf(
                    java.nio.file.Path.of("fixtures", fixtureName),
                    java.nio.file.Path.of("../fixtures", fixtureName),
                    java.nio.file.Path.of("../../fixtures", fixtureName),
                )
                val src = candidates.firstOrNull { Files.exists(it) }
                    ?: error("Fixture not found: $fixtureName")
                Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING)
            }
            else -> error("Provide multipart file= or fixture=")
        }
        return pipeline.processImageSync(
            fixture = tmp,
            instructionText = instructionText,
            locale = locale,
            brandName = brandName ?: "RozPost",
            tenantId = tenantId,
            correlationId = correlationHeader ?: UUID.randomUUID().toString(),
        )
    }

    @PostMapping("/enqueue/download")
    fun enqueueDownload(@RequestBody job: MediaDownloadJob): Map<String, String> {
        val withId = if (job.jobId.isBlank()) job.copy(jobId = UUID.randomUUID().toString()) else job
        queue.enqueueDownload(withId)
        return mapOf("status" to "queued", "queue" to props.queues.download, "jobId" to withId.jobId)
    }

    @PostMapping("/enqueue/enhance")
    fun enqueueEnhance(@RequestBody job: ImageEnhanceJob): Map<String, String> {
        queue.enqueueEnhance(job)
        return mapOf("status" to "queued", "queue" to props.queues.enhance, "jobId" to job.jobId)
    }

    @PostMapping("/enqueue/reel")
    fun enqueueReel(@RequestBody job: ReelRenderJob): Map<String, String> {
        queue.enqueueReel(job)
        return mapOf("status" to "queued", "queue" to props.queues.reel, "jobId" to job.jobId)
    }

    @GetMapping("/wa-mocks")
    fun waMocks(): List<Map<String, Any?>> = notify.recentMocks()

    @GetMapping("/objects/**")
    fun localObject(request: HttpServletRequest): ResponseEntity<ByteArray> {
        val full = request.requestURI.removePrefix("/api/v1/media/objects/")
        val path = storage.localPathFor(full)
        if (!Files.exists(path)) return ResponseEntity.notFound().build()
        val bytes = Files.readAllBytes(path)
        val ct = when {
            full.endsWith(".mp4") -> MediaType.valueOf("video/mp4")
            full.endsWith(".png") -> MediaType.IMAGE_PNG
            else -> MediaType.IMAGE_JPEG
        }
        return ResponseEntity.ok().contentType(ct).body(bytes)
    }
}

@RestController
class HealthController(
    private val storage: ObjectStorageService,
    private val queue: JobQueue,
    private val props: MediaProperties,
) {
    @GetMapping("/health", "/api/health", "/api/v1/health")
    fun health(): Map<String, Any> = mapOf(
        "status" to "ok",
        "ok" to true,
        "service" to "rozpost-worker-media",
        "s3Ready" to storage.isReady(),
        "queues" to mapOf(
            "download" to queue.depth(props.queues.download),
            "enhance" to queue.depth(props.queues.enhance),
            "reel" to queue.depth(props.queues.reel),
            "dlq" to queue.dlqDepth(),
        ),
    )
}

/** Internal sink used when API is co-located / unavailable during local smoke. */
@RestController
@RequestMapping("/api/v1/internal")
class InternalNotifySinkController {
    @PostMapping("/posts/{postId}/assets-ready")
    fun assetsReady(@PathVariable postId: String, @RequestBody body: Map<String, Any?>): Map<String, Any> =
        mapOf("ok" to true, "postId" to postId, "received" to body.keys)
}

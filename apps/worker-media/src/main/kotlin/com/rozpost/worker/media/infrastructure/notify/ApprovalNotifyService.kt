package com.rozpost.worker.media.infrastructure.notify

import com.rozpost.worker.media.domain.model.PipelineResult
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.concurrent.CopyOnWriteArrayList

@Service
class ApprovalNotifyService(
    private val props: MediaProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = restClientBuilder.build()
    private val mockInbox = CopyOnWriteArrayList<Map<String, Any?>>()

    fun assetReadyForApproval(result: PipelineResult) {
        val payload = mapOf(
            "event" to "asset_ready_for_approval",
            "jobId" to result.jobId,
            "tenantId" to result.tenantId,
            "postId" to result.postId,
            "correlationId" to result.correlationId,
            "status" to "awaiting_approval",
            "previewUrls" to result.previewUrls,
            "engine" to result.engine,
            "instructionText" to result.instructionText,
            "actions" to listOf("Approve", "Disapprove"),
        )

        try {
            val token = System.getenv("INTERNAL_API_TOKEN") ?: ""
            var req = http.post()
                .uri("${props.notify.apiBaseUrl.trimEnd('/')}/api/v1/internal/posts/${result.postId}/assets-ready")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", result.correlationId ?: "")
            if (token.isNotBlank()) {
                req = req.header("X-Internal-Token", token)
            }
            req.body(payload).retrieve().toBodilessEntity()
            log.info("Notified API assets-ready postId={} correlationId={}", result.postId, result.correlationId)
        } catch (e: Exception) {
            log.warn("API notify unavailable correlationId={} err={}", result.correlationId, e.message)
        }

        // WA mock sink only when explicitly enabled (feature flag) — not a production default path.
        if (props.notify.waMockEnabled) {
            mockInbox.add(0, payload)
            while (mockInbox.size > 50) mockInbox.removeAt(mockInbox.lastIndex)
            log.info("WA mock notify postId={} correlationId={}", result.postId, result.correlationId)
        }
    }

    fun recentMocks(): List<Map<String, Any?>> = mockInbox.toList()
}

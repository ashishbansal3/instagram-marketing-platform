package com.rozpost.worker.media.infrastructure.queue

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.rozpost.worker.media.application.pipeline.MediaPipelineOrchestrator
import com.rozpost.worker.media.domain.event.DomainEventPublisher
import com.rozpost.worker.media.domain.event.MediaProcessingFailed
import com.rozpost.worker.media.domain.model.ImageEnhanceJob
import com.rozpost.worker.media.domain.model.JobEnvelope
import com.rozpost.worker.media.domain.model.MediaDownloadJob
import com.rozpost.worker.media.domain.model.ReelRenderJob
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["media.consumer.enabled"], havingValue = "true", matchIfMissing = true)
class RedisConsumers(
    private val streams: StreamJobQueue,
    private val mapper: ObjectMapper,
    private val props: MediaProperties,
    private val pipeline: MediaPipelineOrchestrator,
    private val events: DomainEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxAttempts = props.consumer.maxAttempts

    @Scheduled(fixedDelayString = "\${media.consumer.poll-ms:1000}")
    fun consumeDownload() {
        streams.bridgeListToStream(props.queues.downloadList, props.queues.download)
        drain(props.queues.download) { json -> pipeline.handleDownload(parseDownload(json)) }
    }

    @Scheduled(fixedDelayString = "\${media.consumer.poll-ms:1000}")
    fun consumeEnhance() {
        streams.bridgeListToStream(props.queues.enhanceList, props.queues.enhance)
        drain(props.queues.enhance) { json -> pipeline.handleEnhance(parseEnhance(json)) }
    }

    @Scheduled(fixedDelayString = "\${media.consumer.poll-ms:1000}")
    fun consumeReel() {
        streams.bridgeListToStream(props.queues.reelList, props.queues.reel)
        drain(props.queues.reel) { json -> pipeline.handleReel(parseReel(json)) }
    }

    private fun drain(stream: String, handler: (String) -> Unit) {
        val messages = streams.readGroup(stream)
        for (message in messages) {
            val correlationId = extractCorrelation(message.payload) ?: UUID.randomUUID().toString()
            MDC.put("correlationId", correlationId)
            try {
                if (message.deliveryCount > maxAttempts) {
                    streams.toDlq(stream, message, "max attempts exceeded (${message.deliveryCount})")
                    publishFailed(message.payload, stream, "max attempts exceeded")
                    continue
                }
                enrichMdc(message.payload)
                handler(message.payload)
                streams.ack(stream, message.id)
            } catch (e: Exception) {
                log.error(
                    "Failed stream={} id={} deliveries={} correlationId={} err={}",
                    stream, message.id, message.deliveryCount, correlationId, e.message, e,
                )
                if (message.deliveryCount >= maxAttempts) {
                    streams.toDlq(stream, message, e.message ?: e.javaClass.simpleName)
                    publishFailed(message.payload, stream, e.message ?: e.javaClass.simpleName)
                }
                // else: leave pending for PEL reclaim / redelivery (no ACK)
            } finally {
                MDC.remove("correlationId")
                MDC.remove("tenantId")
                MDC.remove("jobId")
                MDC.remove("postId")
            }
        }
    }

    private fun publishFailed(json: String, stream: String, error: String) {
        events.publish(
            MediaProcessingFailed(
                eventId = UUID.randomUUID().toString(),
                correlationId = MDC.get("correlationId"),
                tenantId = extractTenant(json) ?: "unknown",
                postId = extractPost(json) ?: "unknown",
                jobId = extractJobId(json) ?: (MDC.get("correlationId") ?: "unknown"),
                queue = stream,
                error = error,
            ),
        )
    }

    private fun enrichMdc(json: String) {
        extractTenant(json)?.let { MDC.put("tenantId", it) }
        extractJobId(json)?.let { MDC.put("jobId", it) }
        extractPost(json)?.let { MDC.put("postId", it) }
    }

    private fun parseDownload(json: String): MediaDownloadJob {
        val root = mapper.readTree(json)
        if (root.has("payload") && !root.has("sourceUrl") && !root.has("localPath")) {
            val env = mapper.treeToValue<JobEnvelope>(root)
            val p = env.payload ?: error("envelope missing payload")
            return MediaDownloadJob(
                jobId = env.jobId ?: text(p, "jobId") ?: error("jobId required"),
                tenantId = env.tenantId ?: text(p, "tenantId") ?: "unknown-tenant",
                postId = text(p, "postId") ?: text(p, "mediaAssetId") ?: env.jobId ?: "unknown-post",
                sourceUrl = text(p, "sourceUrl") ?: text(p, "fixtureMediaUrl"),
                localPath = text(p, "localPath") ?: text(p, "fixturePath"),
                waMediaId = text(p, "waMediaId"),
                instructionText = text(p, "instructionText"),
                locale = text(p, "locale") ?: "hi-IN",
                kind = text(p, "kind") ?: "image",
                idempotencyKey = env.idempotencyKey,
                correlationId = env.correlationId ?: env.jobId,
            )
        }
        return mapper.readValue(json)
    }

    private fun parseEnhance(json: String): ImageEnhanceJob {
        val root = mapper.readTree(json)
        if (root.has("payload") && !root.has("assetKey")) {
            val env = mapper.treeToValue<JobEnvelope>(root)
            val p = env.payload ?: error("envelope missing payload")
            return ImageEnhanceJob(
                jobId = env.jobId ?: error("jobId required"),
                tenantId = env.tenantId ?: "unknown-tenant",
                postId = text(p, "postId") ?: text(p, "mediaAssetId") ?: env.jobId!!,
                assetKey = text(p, "r2Key") ?: text(p, "assetKey") ?: error("r2Key/assetKey required"),
                instructionText = text(p, "instructionText"),
                locale = text(p, "locale") ?: "hi-IN",
                brandName = text(p, "brandName"),
                idempotencyKey = env.idempotencyKey,
                correlationId = env.correlationId ?: env.jobId,
            )
        }
        return mapper.readValue(json)
    }

    private fun parseReel(json: String): ReelRenderJob {
        val root = mapper.readTree(json)
        if (root.has("payload") && !root.has("assetKey")) {
            val env = mapper.treeToValue<JobEnvelope>(root)
            val p = env.payload ?: error("envelope missing payload")
            return ReelRenderJob(
                jobId = env.jobId ?: error("jobId required"),
                tenantId = env.tenantId ?: "unknown-tenant",
                postId = text(p, "postId") ?: text(p, "mediaAssetId") ?: env.jobId!!,
                assetKey = text(p, "r2Key") ?: text(p, "assetKey") ?: error("r2Key/assetKey required"),
                instructionText = text(p, "instructionText"),
                caption = text(p, "caption"),
                locale = text(p, "locale") ?: "hi-IN",
                brandName = text(p, "brandName"),
                idempotencyKey = env.idempotencyKey,
                correlationId = env.correlationId ?: env.jobId,
            )
        }
        return mapper.readValue(json)
    }

    private fun text(node: JsonNode, field: String): String? =
        node.get(field)?.takeUnless { it.isNull }?.asText()?.takeIf { it.isNotBlank() }

    private fun extractCorrelation(json: String): String? = runCatching {
        val n = mapper.readTree(json)
        n.path("correlationId").asText(null)?.takeIf { it.isNotBlank() }
            ?: n.path("jobId").asText(null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractTenant(json: String): String? = runCatching {
        mapper.readTree(json).path("tenantId").asText(null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractPost(json: String): String? = runCatching {
        mapper.readTree(json).path("postId").asText(null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractJobId(json: String): String? = runCatching {
        mapper.readTree(json).path("jobId").asText(null)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

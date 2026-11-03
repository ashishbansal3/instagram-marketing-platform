package com.rozpost.worker.publish.interfaces

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.publish.application.PublishJobProcessor
import com.rozpost.worker.publish.infrastructure.config.PublishProperties
import com.rozpost.worker.publish.infrastructure.queue.StreamJobQueue
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Redis Streams consumer for ig-publish — ACK on success, PEL redelivery on failure,
 * max-attempts → DLQ. Bridges legacy LIST producers from the API.
 */
@Component
@ConditionalOnProperty(name = ["publish.consumer.enabled"], havingValue = "true", matchIfMissing = true)
class IgPublishConsumer(
    private val streams: StreamJobQueue,
    private val props: PublishProperties,
    private val processor: PublishJobProcessor,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxAttempts = props.consumer.maxAttempts

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        streams.ensureGroup(props.queues.publish)
        streams.ensureGroup(props.queues.dlq)
        log.info(
            "ig-publish Streams consumer ready stream={} group=worker-publish maxAttempts={}",
            props.queues.publish,
            maxAttempts,
        )
    }

    @Scheduled(fixedDelayString = "\${publish.consumer.poll-ms:1000}")
    fun consumePublish() {
        streams.bridgeListToStream(props.queues.publishList, props.queues.publish)
        streams.bridgeListToStream(props.queues.delayedList, props.queues.delayed)
        drain(props.queues.publish)
        // Re-drive delayed stream into main stream when due (simple MVP: bridge all delayed)
        drainDelayed()
        drainDbFallback()
    }

    private fun drain(stream: String) {
        val messages = streams.readGroup(stream)
        for (message in messages) {
            val correlationId = extractCorrelation(message.payload) ?: UUID.randomUUID().toString()
            MDC.put("correlationId", correlationId)
            try {
                if (message.deliveryCount > maxAttempts) {
                    streams.toDlq(stream, message, "max attempts exceeded (${message.deliveryCount})")
                    continue
                }
                enrichMdc(message.payload)
                processor.processEnvelope(message.payload)
                streams.ack(stream, message.id)
            } catch (e: Exception) {
                log.error(
                    "Failed stream={} id={} deliveries={} correlationId={} err={}",
                    stream,
                    message.id,
                    message.deliveryCount,
                    correlationId,
                    e.message,
                    e,
                )
                if (message.deliveryCount >= maxAttempts) {
                    streams.toDlq(stream, message, e.message ?: e.javaClass.simpleName)
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

    private fun drainDelayed() {
        val messages = streams.readGroup(props.queues.delayed)
        for (message in messages) {
            try {
                streams.enqueuePublish(message.payload)
                streams.ack(props.queues.delayed, message.id)
            } catch (e: Exception) {
                log.warn("delayed bridge failed id={} err={}", message.id, e.message)
                if (message.deliveryCount >= maxAttempts) {
                    streams.toDlq(props.queues.delayed, message, e.message ?: "delayed_bridge_failed")
                }
            }
        }
    }

    /** DB-queued jobs without a Redis message (crash recovery). */
    private fun drainDbFallback() {
        try {
            val pending = jdbc.query(
                """
                SELECT payload::text FROM jobs
                WHERE type = 'ig_publish' AND status = 'queued'
                  AND (scheduled_for IS NULL OR scheduled_for <= now())
                ORDER BY created_at ASC LIMIT 5
                """.trimIndent(),
            ) { rs, _ -> rs.getString(1) }
            pending.forEach { json ->
                streams.enqueuePublish(json)
            }
        } catch (ex: Exception) {
            // scheduled_for may not exist on older schemas — fall back without filter
            try {
                val pending = jdbc.query(
                    """
                    SELECT payload::text FROM jobs
                    WHERE type = 'ig_publish' AND status = 'queued'
                    ORDER BY created_at ASC LIMIT 5
                    """.trimIndent(),
                ) { rs, _ -> rs.getString(1) }
                pending.forEach { streams.enqueuePublish(it) }
            } catch (e: Exception) {
                log.warn("db fallback enqueue error: {}", e.message)
            }
        }
    }

    private fun enrichMdc(json: String) {
        runCatching {
            val n = mapper.readTree(json)
            n.path("tenantId").asText(null)?.takeIf { it.isNotBlank() }?.let { MDC.put("tenantId", it) }
            n.path("jobId").asText(null)?.takeIf { it.isNotBlank() }?.let { MDC.put("jobId", it) }
            n.path("payload").path("postId").asText(null)?.takeIf { it.isNotBlank() }
                ?.let { MDC.put("postId", it) }
        }
    }

    private fun extractCorrelation(json: String): String? = runCatching {
        val n = mapper.readTree(json)
        n.path("payload").path("correlationId").asText(null)?.takeIf { it.isNotBlank() }
            ?: n.path("correlationId").asText(null)?.takeIf { it.isNotBlank() }
            ?: n.path("jobId").asText(null)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

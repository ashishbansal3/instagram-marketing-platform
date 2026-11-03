package com.rozpost.worker.publish.infrastructure.queue

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.publish.infrastructure.config.PublishProperties
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.Consumer as RedisConsumer
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

data class StreamMessage(
    val id: String,
    val payload: String,
    val deliveryCount: Long,
)

/**
 * Redis Streams producer/consumer with consumer-group ACK, delivery counting, and DLQ.
 * Bridges legacy Redis list LPUSH jobs (API contract) into the stream.
 */
@Component
class StreamJobQueue(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val props: PublishProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val group = "worker-publish"
    private val consumerName =
        "worker-${System.getenv("HOSTNAME") ?: "local"}-${ProcessHandle.current().pid()}"

    fun ensureGroup(stream: String) {
        try {
            // 0-0: include messages XADD'd before this worker created the group.
            redis.opsForStream<String, String>().createGroup(stream, ReadOffset.from("0-0"), group)
        } catch (_: Exception) {
            // group may already exist
        }
    }

    fun enqueuePublish(payload: String): String = xadd(props.queues.publish, payload)

    fun enqueueDelayed(payload: String): String = xadd(props.queues.delayed, payload)

    fun xadd(stream: String, payload: String): String {
        ensureGroup(stream)
        val id = redis.opsForStream<String, String>().add(
            MapRecord.create(
                stream,
                mapOf(
                    "payload" to payload,
                    "correlationId" to (MDC.get("correlationId") ?: ""),
                    "enqueuedAt" to java.time.Instant.now().toString(),
                ),
            ),
        )
        log.info("XADD stream={} id={} correlationId={}", stream, id, MDC.get("correlationId"))
        return id?.value ?: ""
    }

    /** Bridge legacy list producers (LPUSH) into the stream. */
    fun bridgeListToStream(listKey: String, stream: String, max: Int = 20) {
        ensureGroup(stream)
        repeat(max) {
            val json = redis.opsForList().rightPop(listKey) ?: return
            redis.opsForStream<String, String>().add(
                MapRecord.create(
                    stream,
                    mapOf(
                        "payload" to json,
                        "bridgedFrom" to listKey,
                        "enqueuedAt" to java.time.Instant.now().toString(),
                    ),
                ),
            )
        }
    }

    fun readGroup(stream: String, count: Int = 5): List<StreamMessage> {
        ensureGroup(stream)
        val pending = redis.opsForStream<String, String>().read(
            RedisConsumer.from(group, consumerName),
            StreamReadOptions.empty().count(count.toLong()),
            StreamOffset.create(stream, ReadOffset.from("0")),
        )
        val records = if (!pending.isNullOrEmpty()) {
            pending
        } else {
            redis.opsForStream<String, String>().read(
                RedisConsumer.from(group, consumerName),
                StreamReadOptions.empty().count(count.toLong()).block(Duration.ofMillis(200)),
                StreamOffset.create(stream, ReadOffset.lastConsumed()),
            ) ?: return emptyList()
        }
        return records.map { rec ->
            val payload = rec.value["payload"]
                ?: rec.value["envelope"]
                ?: mapper.writeValueAsString(rec.value)
            val deliveries = deliveryCount(stream, rec.id)
            StreamMessage(rec.id.value, payload, deliveries)
        }
    }

    fun ack(stream: String, id: String) {
        redis.opsForStream<String, String>().acknowledge(stream, group, id)
    }

    fun toDlq(sourceStream: String, message: StreamMessage, error: String) {
        ensureGroup(props.queues.dlq)
        val envelope = mapOf(
            "sourceStream" to sourceStream,
            "sourceId" to message.id,
            "error" to error,
            "deliveryCount" to message.deliveryCount.toString(),
            "correlationId" to (MDC.get("correlationId") ?: ""),
            "payload" to message.payload,
            "failedAt" to java.time.Instant.now().toString(),
        )
        redis.opsForStream<String, String>().add(MapRecord.create(props.queues.dlq, envelope))
        ack(sourceStream, message.id)
        log.error(
            "DLQ stream={} source={} id={} deliveries={} correlationId={} err={}",
            props.queues.dlq,
            sourceStream,
            message.id,
            message.deliveryCount,
            MDC.get("correlationId"),
            error,
        )
    }

    fun depthApprox(stream: String): Long =
        try {
            redis.opsForStream<String, String>().size(stream) ?: 0L
        } catch (_: Exception) {
            0L
        }

    private fun deliveryCount(stream: String, id: RecordId): Long {
        return try {
            val range = org.springframework.data.domain.Range.closed(id.value, id.value)
            val pending = redis.opsForStream<String, String>().pending(stream, group, range, 1L)
            pending?.firstOrNull()?.getTotalDeliveryCount() ?: 1L
        } catch (_: Exception) {
            1L
        }
    }
}

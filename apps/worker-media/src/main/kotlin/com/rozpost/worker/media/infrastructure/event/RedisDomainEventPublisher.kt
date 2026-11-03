package com.rozpost.worker.media.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.media.domain.event.DomainEvent
import com.rozpost.worker.media.domain.event.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisDomainEventPublisher(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
) : DomainEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)
    private val channel = "rozpost.domain.events"
    /** Must match API LoggingDomainEventBus — Redis Stream, not a list (WRONGTYPE). */
    private val streamKey = "domain-events"

    override fun publish(event: DomainEvent) {
        val json = mapper.writeValueAsString(event)
        redis.convertAndSend(channel, json)
        redis.opsForStream<String, String>().add(
            MapRecord.create(
                streamKey,
                mapOf(
                    "type" to event.type,
                    "tenantId" to event.tenantId,
                    "correlationId" to (event.correlationId ?: ""),
                    "payload" to json,
                ),
            ),
        )
        log.info(
            "domain_event type={} tenantId={} correlationId={} eventId={}",
            event.type,
            event.tenantId,
            event.correlationId,
            event.eventId,
        )
    }
}

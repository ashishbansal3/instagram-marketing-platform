package com.rozpost.worker.media.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.media.domain.event.DomainEvent
import com.rozpost.worker.media.domain.event.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisDomainEventPublisher(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
) : DomainEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)
    private val channel = "rozpost.domain.events"

    override fun publish(event: DomainEvent) {
        val json = mapper.writeValueAsString(event)
        redis.convertAndSend(channel, json)
        redis.opsForList().leftPush("domain-events", json)
        log.info(
            "domain_event type={} tenantId={} correlationId={} eventId={}",
            event.type,
            event.tenantId,
            event.correlationId,
            event.eventId,
        )
    }
}

package com.rozpost.api.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.event.Approved;
import com.rozpost.api.domain.event.Disapproved;
import com.rozpost.api.domain.event.DomainEvent;
import com.rozpost.api.domain.event.JobQueued;
import com.rozpost.api.domain.event.MediaProcessed;
import com.rozpost.api.domain.event.MediaReceived;
import com.rozpost.api.domain.event.OnboardingCompleted;
import com.rozpost.api.domain.event.Published;
import com.rozpost.api.domain.event.ReminderSent;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.port.DomainEventSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persist domain event (outbox-friendly) then fan-out to Redis Streams + subscribers.
 * Producers never call notification/billing directly.
 */
@Component
public class LoggingDomainEventBus implements DomainEventBus {
    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventBus.class);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final List<DomainEventSubscriber> subscribers;

    public LoggingDomainEventBus(
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            ObjectMapper mapper,
            List<DomainEventSubscriber> subscribers
    ) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.mapper = mapper;
        this.subscribers = subscribers;
    }

    @Override
    public void publish(DomainEvent event) {
        log.info(
                "domain_event type={} tenantId={} correlationId={}",
                event.type(),
                event.tenantId(),
                event.correlationId());
        try {
            String payload = mapper.writeValueAsString(event);
            UUID aggregateId = extractAggregateId(event);
            String aggregateType = aggregateType(event);
            jdbc.update(
                    """
                    INSERT INTO domain_events (tenant_id, event_type, aggregate_type, aggregate_id, correlation_id, payload)
                    VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::jsonb)
                    """,
                    event.tenantId() == null ? null : event.tenantId().toString(),
                    event.type(),
                    aggregateType,
                    aggregateId.toString(),
                    event.correlationId(),
                    payload);
            redis.opsForStream()
                    .add(
                            "domain-events",
                            Map.of(
                                    "type", event.type(),
                                    "tenantId", event.tenantId() == null ? "" : event.tenantId().toString(),
                                    "correlationId", event.correlationId() == null ? "" : event.correlationId(),
                                    "aggregateId", aggregateId.toString(),
                                    "payload", payload));
            for (DomainEventSubscriber sub : subscribers) {
                if ("*".equals(sub.eventType()) || sub.eventType().equals(event.type())) {
                    sub.onEvent(event);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist domain event " + event.type(), e);
        }
    }

    private static String aggregateType(DomainEvent event) {
        return switch (event.type()) {
            case "Approved", "Disapproved", "Published", "MediaProcessed", "ReminderSent" -> "post";
            case "JobQueued" -> "job";
            case "MediaReceived" -> "media";
            case "OnboardingCompleted" -> "client";
            default -> "domain";
        };
    }

    private static UUID extractAggregateId(DomainEvent event) {
        if (event instanceof MediaProcessed mp) {
            return mp.postId();
        }
        if (event instanceof Approved a) {
            try {
                return UUID.fromString(a.postKey());
            } catch (Exception ignored) {
                return a.tenantId() != null ? a.tenantId() : UUID.randomUUID();
            }
        }
        if (event instanceof Disapproved d) {
            return d.postId();
        }
        if (event instanceof Published p) {
            return p.postId();
        }
        if (event instanceof ReminderSent r) {
            return r.postId();
        }
        if (event instanceof JobQueued j) {
            return j.jobId();
        }
        if (event instanceof OnboardingCompleted oc) {
            return oc.clientId();
        }
        if (event instanceof MediaReceived) {
            return event.tenantId() != null ? event.tenantId() : UUID.randomUUID();
        }
        return event.tenantId() != null ? event.tenantId() : UUID.randomUUID();
    }
}

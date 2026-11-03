package com.rozpost.api.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.event.DomainEvent;
import com.rozpost.api.domain.port.DomainEventBus;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoggingDomainEventBus implements DomainEventBus {
  private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventBus.class);

  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public LoggingDomainEventBus(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.mapper = mapper;
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
      jdbc.update(
          """
          INSERT INTO domain_events (tenant_id, event_type, aggregate_type, aggregate_id, correlation_id, payload)
          VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::jsonb)
          """,
          event.tenantId() == null ? null : event.tenantId().toString(),
          event.type(),
          "domain",
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
                  "payload", payload));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to persist domain event " + event.type(), e);
    }
  }

  private static UUID extractAggregateId(DomainEvent event) {
    try {
      var m = event.getClass().getMethod("postId");
      Object v = m.invoke(event);
      if (v instanceof UUID u) {
        return u;
      }
      if (v != null) {
        return UUID.fromString(v.toString());
      }
    } catch (ReflectiveOperationException ignored) {
      // fall through
    }
    return event.tenantId() != null ? event.tenantId() : UUID.randomUUID();
  }
}

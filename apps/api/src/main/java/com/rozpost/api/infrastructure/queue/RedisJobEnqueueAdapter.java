package com.rozpost.api.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rozpost.api.domain.port.JobEnqueuePort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisJobEnqueueAdapter implements JobEnqueuePort {
  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public RedisJobEnqueueAdapter(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.mapper = mapper;
  }

  @Override
  public UUID enqueue(
      UUID tenantId,
      UUID clientId,
      String type,
      String queueName,
      String idempotencyKey,
      Map<String, Object> payload) {
    UUID existing =
        jdbc.query(
            "SELECT id FROM jobs WHERE idempotency_key = ?",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null,
            idempotencyKey);
    if (existing != null) {
      return existing;
    }

    UUID jobId = UUID.randomUUID();
    ObjectNode envelope = mapper.createObjectNode();
    envelope.put("jobId", jobId.toString());
    envelope.put("tenantId", tenantId.toString());
    if (clientId != null) {
      envelope.put("clientId", clientId.toString());
    }
    envelope.put("type", type);
    envelope.put("idempotencyKey", idempotencyKey);
    envelope.put("createdAt", Instant.now().toString());
    envelope.set("payload", mapper.valueToTree(payload));
    String json = envelope.toString();

    jdbc.update(
        """
        INSERT INTO jobs (id, tenant_id, type, status, idempotency_key, payload)
        VALUES (?::uuid, ?::uuid, ?, 'queued', ?, ?::jsonb)
        ON CONFLICT (idempotency_key) DO NOTHING
        """,
        jobId.toString(),
        tenantId.toString(),
        type,
        idempotencyKey,
        json);

    // Legacy list key for bridge; Streams use stream.<queue> (never dual-type the same Redis key).
    redis.opsForList().leftPush(queueName, json);
    String stream = queueName.startsWith("stream.") ? queueName : "stream." + queueName;
    redis.opsForStream()
        .add(
            stream,
            Map.of(
                "payload", json,
                "jobId", jobId.toString(),
                "type", type,
                "correlationId",
                    payload.get("correlationId") == null
                        ? ""
                        : String.valueOf(payload.get("correlationId"))));
    return jobId;
  }
}

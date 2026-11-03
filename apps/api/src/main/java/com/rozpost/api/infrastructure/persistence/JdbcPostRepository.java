package com.rozpost.api.infrastructure.persistence;

import com.rozpost.api.domain.port.PostRepositoryPort;
import com.rozpost.api.domain.post.PostStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPostRepository implements PostRepositoryPort {
  private final JdbcTemplate jdbc;

  public JdbcPostRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Map<String, Object>> findById(UUID postId) {
    return Optional.ofNullable(
        jdbc.query(
            """
            SELECT id, tenant_id, client_id, status, caption, instruction_text,
                   feedback_text, retry_count, max_retries, ig_permalink, ig_media_id
            FROM posts WHERE id = ?::uuid
            """,
            rs -> {
              if (!rs.next()) {
                return null;
              }
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", rs.getObject("id", UUID.class));
              m.put("tenant_id", rs.getObject("tenant_id", UUID.class));
              m.put("client_id", rs.getObject("client_id", UUID.class));
              m.put("status", rs.getString("status"));
              m.put("caption", rs.getString("caption"));
              m.put("instruction_text", rs.getString("instruction_text"));
              m.put("feedback_text", rs.getString("feedback_text"));
              m.put("retry_count", rs.getInt("retry_count"));
              m.put("max_retries", rs.getInt("max_retries"));
              m.put("ig_permalink", rs.getString("ig_permalink"));
              m.put("ig_media_id", rs.getString("ig_media_id"));
              return m;
            },
            postId.toString()));
  }

  @Override
  public void updateStatus(UUID postId, PostStatus status) {
    jdbc.update(
        "UPDATE posts SET status = ?, updated_at = now() WHERE id = ?::uuid",
        status.name(),
        postId.toString());
  }

  @Override
  public void updateAfterDisapprove(
      UUID postId, String feedback, int retryCount, PostStatus status, String caption) {
    jdbc.update(
        """
        UPDATE posts SET status = ?, feedback_text = ?, retry_count = ?,
          caption = COALESCE(?, caption), updated_at = now()
        WHERE id = ?::uuid
        """,
        status.name(),
        feedback,
        retryCount,
        caption,
        postId.toString());
  }

  @Override
  public void recordApproval(
      UUID postId, UUID tenantId, String waMessageId, String decision, String note) {
    jdbc.update(
        """
        INSERT INTO approvals (post_id, tenant_id, channel, wa_message_id, decision, note)
        VALUES (?::uuid, ?::uuid, 'whatsapp', ?, ?, ?)
        """,
        postId.toString(),
        tenantId.toString(),
        waMessageId,
        decision,
        note);
  }

  @Override
  public Optional<String> findIgUserId(UUID tenantId) {
    return Optional.ofNullable(
        jdbc.query(
            """
            SELECT ig_user_id FROM meta_connections
            WHERE tenant_id = ?::uuid AND provider = 'instagram_login' AND status = 'active'
            LIMIT 1
            """,
            rs -> rs.next() ? rs.getString(1) : null,
            tenantId.toString()));
  }

  @Override
  public Optional<UUID> findLatestAwaitingApproval(String waUserId) {
    return Optional.ofNullable(
        jdbc.query(
            """
            SELECT p.id FROM posts p
            JOIN clients c ON c.id = p.client_id
            WHERE c.wa_user_id = ? AND p.status IN ('awaiting_approval', 'drafted')
            ORDER BY p.created_at DESC LIMIT 1
            """,
            rs -> rs.next() ? (UUID) rs.getObject(1) : null,
            waUserId));
  }

  @Override
  public String findClientWaUserId(UUID clientId) {
    return jdbc.query(
        "SELECT wa_user_id FROM clients WHERE id = ?::uuid",
        rs -> rs.next() ? rs.getString(1) : null,
        clientId.toString());
  }
}

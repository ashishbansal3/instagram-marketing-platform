package com.rozpost.api.interfaces.rest.v1;

import com.rozpost.api.domain.post.PostStatus;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import com.rozpost.api.interfaces.error.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Worker → API callbacks (media pipeline). Authenticated via INTERNAL_API_TOKEN when set.
 */
@RestController
@RequestMapping("/api/v1/internal/posts")
public class InternalPostsV1Controller {
  private final JdbcTemplate jdbc;
  private final RozpostProperties props;

  public InternalPostsV1Controller(JdbcTemplate jdbc, RozpostProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  @PostMapping("/{id}/assets-ready")
  public Map<String, Object> assetsReady(
      @PathVariable("id") UUID postId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Internal-Token", required = false) String token) {
    assertInternal(token);

    Integer exists =
        jdbc.query(
            "SELECT 1 FROM posts WHERE id = ?::uuid",
            rs -> rs.next() ? 1 : null,
            postId.toString());
    if (exists == null) {
      throw ApiException.notFound("post_not_found", "post not found: " + postId);
    }

    String caption =
        body.get("instructionText") == null
            ? null
            : body.get("instructionText").toString() + " — enhanced preview";
    Object engine = body.get("engine");
    Object preview = body.get("previewUrls");

    jdbc.update(
        """
        UPDATE posts SET status = ?, caption = COALESCE(?, caption), updated_at = now()
        WHERE id = ?::uuid
        """,
        PostStatus.awaiting_approval.name(),
        caption,
        postId.toString());

    Map<String, Object> post =
        jdbc.query(
            """
            SELECT tenant_id, client_id FROM posts WHERE id = ?::uuid
            """,
            rs -> {
              if (!rs.next()) {
                return null;
              }
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("tenant_id", rs.getObject("tenant_id", UUID.class));
              m.put("client_id", rs.getObject("client_id", UUID.class));
              return m;
            },
            postId.toString());

    if (post != null) {
      jdbc.update(
          """
          INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
          VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, ?)
          """,
          post.get("tenant_id").toString(),
          post.get("client_id") == null ? null : post.get("client_id").toString(),
          postId.toString(),
          "Preview ready. Reply Approve or Disapprove.",
          "packages/meta-mocks/fixtures/whatsapp/outbound-approval-template.json");
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("postId", postId.toString());
    out.put("status", PostStatus.awaiting_approval.name());
    if (engine != null) {
      out.put("engine", engine.toString());
    }
    if (preview != null) {
      out.put("previewUrls", preview);
    }
    return out;
  }

  private void assertInternal(String token) {
    String expected = props.getInternalApiToken();
    if (expected == null || expected.isBlank()) {
      return;
    }
    if (token == null || !expected.equals(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
    }
  }
}

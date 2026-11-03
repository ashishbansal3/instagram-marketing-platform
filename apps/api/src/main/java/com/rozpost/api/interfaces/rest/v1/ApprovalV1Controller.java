package com.rozpost.api.interfaces.rest.v1;

import com.rozpost.api.application.approval.ApprovalCommandService;
import com.rozpost.api.interfaces.error.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApprovalV1Controller {
  /** Required on GET /posts/{id} — cross-tenant reads must 404. */
  public static final String TENANT_HEADER = "X-Tenant-Id";

  private final ApprovalCommandService approval;
  private final JdbcTemplate jdbc;

  public ApprovalV1Controller(ApprovalCommandService approval, JdbcTemplate jdbc) {
    this.approval = approval;
    this.jdbc = jdbc;
  }

  /** Production path for WhatsApp interactive button simulation / operator tooling. */
  @PostMapping("/approvals/wa-button")
  public Map<String, Object> waButton(@RequestBody Map<String, Object> body) {
    Object postIdRaw = body.get("postId");
    if (postIdRaw == null) {
      throw ApiException.badRequest("missing_field", "postId required");
    }
    UUID postId = UUID.fromString(postIdRaw.toString());
    String button = body.get("button") == null ? null : body.get("button").toString();
    String waUserId = body.get("waUserId") == null ? null : body.get("waUserId").toString();
    String feedback = body.get("feedbackText") == null ? null : body.get("feedbackText").toString();
    String waMessageId =
        body.get("waMessageId") == null ? null : body.get("waMessageId").toString();

    Map<String, Object> result =
        approval.handleButton(waUserId, postId, button, feedback, waMessageId);
    var snap = approval.snapshot(postId);
    result.put("finalStatus", snap.status());
    result.put("igPermalink", snap.igPermalink());
    result.put("igMediaId", snap.igMediaId());
    // PostSnapshot record accessors

    var outbound =
        jdbc.query(
            """
            SELECT body FROM outbound_messages
            WHERE post_id = ?::uuid ORDER BY created_at DESC LIMIT 1
            """,
            rs -> rs.next() ? rs.getString(1) : null,
            postId.toString());
    if (outbound != null) {
      result.put("outboundBody", outbound);
    }
    return result;
  }

  @GetMapping("/posts/{id}")
  public Map<String, Object> getPost(
      @PathVariable("id") UUID id,
      @RequestHeader(value = TENANT_HEADER, required = false) String tenantHeader) {
    UUID tenantId = parseTenantHeader(tenantHeader);
    var snap = approval.snapshotForTenant(id, tenantId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", snap.id());
    out.put("status", snap.status());
    out.put("retryCount", snap.retryCount());
    out.put("maxRetries", snap.maxRetries());
    out.put("caption", snap.caption());
    out.put("instructionText", snap.instructionText());
    out.put("feedbackText", snap.feedbackText());
    out.put("igPermalink", snap.igPermalink());
    out.put("igMediaId", snap.igMediaId());
    return out;
  }

  private static UUID parseTenantHeader(String raw) {
    if (raw == null || raw.isBlank()) {
      throw ApiException.badRequest("missing_tenant", "X-Tenant-Id required");
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("invalid_tenant", "X-Tenant-Id must be a UUID");
    }
  }
}

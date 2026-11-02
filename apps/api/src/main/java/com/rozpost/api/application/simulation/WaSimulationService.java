package com.rozpost.api.application.simulation;

import com.rozpost.api.application.port.JobEnqueuePort;
import com.rozpost.api.domain.event.MediaProcessed;
import com.rozpost.api.domain.event.MediaReceived;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.post.PostStatus;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import com.rozpost.api.interfaces.error.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** META_MODE=mock simulation of WA media intake for smoke tests. */
@Service
public class WaSimulationService {
  private final JdbcTemplate jdbc;
  private final JobEnqueuePort jobs;
  private final DomainEventBus events;
  private final String metaMode;
  private final boolean mockAutoEnhance;

  public WaSimulationService(
      JdbcTemplate jdbc,
      JobEnqueuePort jobs,
      DomainEventBus events,
      @Value("${rozpost.meta-mode:mock}") String metaMode,
      @Value("${rozpost.mock-auto-enhance:true}") boolean mockAutoEnhance) {
    this.jdbc = jdbc;
    this.jobs = jobs;
    this.events = events;
    this.metaMode = metaMode;
    this.mockAutoEnhance = mockAutoEnhance;
  }

  @Transactional
  public Map<String, Object> simulateInboundMedia(Map<String, Object> body) {
    String waUserId = required(body, "waUserId");
    Map<String, Object> client =
        jdbc.query(
            "SELECT id, tenant_id FROM clients WHERE wa_user_id = ? LIMIT 1",
            rs -> {
              if (!rs.next()) {
                throw ApiException.badRequest("unknown_wa_user", "unknown waUserId — onboard first");
              }
              return Map.<String, Object>of(
                  "id", rs.getObject("id", UUID.class),
                  "tenant_id", rs.getObject("tenant_id", UUID.class));
            },
            waUserId);

    UUID tenantId = (UUID) client.get("tenant_id");
    UUID clientId = (UUID) client.get("id");
    String instruction =
        str(body, "instructionText", str(body, "caption", "Enhance product photo"));
    String waMessageId = str(body, "waMessageId", "wamid.sim." + UUID.randomUUID());
    String waMediaId = str(body, "waMediaId", "media-" + UUID.randomUUID());
    String correlationId = CorrelationIdFilter.current();

    UUID assetId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO media_assets (id, tenant_id, source, kind, r2_key, instruction_text, wa_media_id)
        VALUES (?::uuid, ?::uuid, 'whatsapp', 'image', ?, ?, ?)
        """,
        assetId.toString(),
        tenantId.toString(),
        "tenant/" + tenantId + "/inbound/" + assetId + ".jpg",
        instruction,
        waMediaId);

    UUID postId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO posts (id, tenant_id, client_id, type, status, caption, instruction_text,
          source_asset_ids, retry_count, max_retries)
        VALUES (?::uuid, ?::uuid, ?::uuid, 'image', ?, ?, ?, ARRAY[?::uuid], 0, 3)
        """,
        postId.toString(),
        tenantId.toString(),
        clientId.toString(),
        PostStatus.intake_received.name(),
        instruction,
        instruction,
        assetId.toString());

    Map<String, Object> dlPayload = new LinkedHashMap<>();
    dlPayload.put("mediaAssetId", assetId.toString());
    dlPayload.put("waMediaId", waMediaId);
    dlPayload.put("waMessageId", waMessageId);
    dlPayload.put("instructionText", instruction);
    dlPayload.put("postId", postId.toString());
    dlPayload.put("metaMode", metaMode);
    dlPayload.put("correlationId", correlationId);

    UUID dlJob =
        jobs.enqueue(
            tenantId, clientId, "media_download", "media-download", "wamid:" + waMessageId, dlPayload);

    events.publish(
        new MediaReceived(tenantId, correlationId, waMessageId, waMediaId, instruction, Instant.now()));

    jdbc.update(
        "UPDATE posts SET status = ?, updated_at = now() WHERE id = ?::uuid",
        PostStatus.media_processing.name(),
        postId.toString());

    Map<String, Object> enhancePayload = new LinkedHashMap<>();
    enhancePayload.put("mediaAssetId", assetId.toString());
    enhancePayload.put("r2Key", "tenant/" + tenantId + "/inbound/" + assetId + ".jpg");
    enhancePayload.put("instructionText", instruction);
    enhancePayload.put("postId", postId.toString());
    enhancePayload.put("correlationId", correlationId);
    UUID enhanceJob =
        jobs.enqueue(
            tenantId, clientId, "image_enhance", "image-enhance", "enhance:" + postId, enhancePayload);

    String status = PostStatus.media_processing.name();
    if ("mock".equalsIgnoreCase(metaMode) && mockAutoEnhance) {
      UUID outputId = UUID.randomUUID();
      jdbc.update(
          """
          UPDATE posts SET status = ?, caption = ?, output_asset_ids = ARRAY[?::uuid], updated_at = now()
          WHERE id = ?::uuid
          """,
          PostStatus.awaiting_approval.name(),
          instruction + " — enhanced preview",
          outputId.toString(),
          postId.toString());
      jdbc.update(
          """
          INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
          VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, ?)
          """,
          tenantId.toString(),
          clientId.toString(),
          postId.toString(),
          "Preview ready. Reply Approve or Disapprove.",
          "packages/meta-mocks/fixtures/whatsapp/outbound-approval-template.json");
      events.publish(
          new MediaProcessed(
              tenantId, correlationId, postId, outputId.toString(), Instant.now()));
      status = PostStatus.awaiting_approval.name();
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("tenantId", tenantId.toString());
    out.put("clientId", clientId.toString());
    out.put("postId", postId.toString());
    out.put("mediaAssetId", assetId.toString());
    out.put("status", status);
    out.put("downloadJobId", dlJob.toString());
    out.put("enhanceJobId", enhanceJob.toString());
    out.put("instructionText", instruction);
    out.put("correlationId", correlationId);
    return out;
  }

  private static String required(Map<String, Object> body, String key) {
    String v = str(body, key, null);
    if (v == null || v.isBlank()) {
      throw ApiException.badRequest("missing_field", key + " required");
    }
    return v;
  }

  private static String str(Map<String, Object> body, String key, String fallback) {
    Object v = body.get(key);
    if (v == null || v.toString().isBlank()) {
      return fallback;
    }
    return v.toString();
  }
}

package com.rozpost.api.application.approval;

import com.rozpost.api.domain.port.JobEnqueuePort;
import com.rozpost.api.domain.port.PostRepositoryPort;
import com.rozpost.api.domain.approval.ApprovalDecision;
import com.rozpost.api.domain.event.Approved;
import com.rozpost.api.domain.event.Disapproved;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.post.PostStatus;
import com.rozpost.api.domain.publish.PublishTargetType;
import com.rozpost.api.infrastructure.logging.CorrelationIdFilter;
import com.rozpost.api.interfaces.error.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalCommandService {
  private static final Logger log = LoggerFactory.getLogger(ApprovalCommandService.class);

  private final PostRepositoryPort posts;
  private final JobEnqueuePort jobs;
  private final DomainEventBus events;
  private final String metaMode;
  private final String fixturePermalink;
  private final int quotaPerDay;
  private final boolean mockAutoEnhance;

  public ApprovalCommandService(
      PostRepositoryPort posts,
      JobEnqueuePort jobs,
      DomainEventBus events,
      @Value("${rozpost.meta-mode:mock}") String metaMode,
      @Value("${rozpost.fixture-permalink:https://www.instagram.com/p/DEMO_ROZPOST_POSTED/}")
          String fixturePermalink,
      @Value("${rozpost.publish-quota-per-day:25}") int quotaPerDay,
      @Value("${rozpost.mock-auto-enhance:true}") boolean mockAutoEnhance) {
    this.posts = posts;
    this.jobs = jobs;
    this.events = events;
    this.metaMode = metaMode;
    this.fixturePermalink = fixturePermalink;
    this.quotaPerDay = quotaPerDay;
    this.mockAutoEnhance = mockAutoEnhance;
  }

  @Transactional
  public Map<String, Object> handleButton(
      String waUserId, UUID postId, String button, String feedbackText, String waMessageId) {
    Map<String, Object> post =
        posts
            .findById(postId)
            .orElseThrow(() -> ApiException.notFound("post_not_found", "post not found: " + postId));

    UUID tenantId = (UUID) post.get("tenant_id");
    UUID clientId = (UUID) post.get("client_id");
    PostStatus status = PostStatus.valueOf((String) post.get("status"));
    int retryCount = ((Number) post.get("retry_count")).intValue();
    int maxRetries = ((Number) post.get("max_retries")).intValue();

    if (waUserId != null && !waUserId.isBlank() && clientId != null) {
      String owner = posts.findClientWaUserId(clientId);
      if (owner != null && !owner.equals(waUserId)) {
        throw ApiException.badRequest("wa_user_mismatch", "waUserId does not own this post");
      }
    }
    if (!status.canDecideApproval()) {
      throw ApiException.conflict(
          "invalid_post_status", "post not awaiting approval; status=" + status);
    }

    ApprovalDecision decision = ApprovalDecision.fromButton(button);
    String correlationId = CorrelationIdFilter.current();
    posts.recordApproval(
        postId,
        tenantId,
        waMessageId != null ? waMessageId : "btn-" + UUID.randomUUID(),
        decision.name(),
        feedbackText);

    if (decision == ApprovalDecision.approved) {
      return approve(postId, tenantId, clientId, post, correlationId, waMessageId);
    }
    return disapprove(
        postId, tenantId, clientId, post, feedbackText, retryCount, maxRetries, correlationId);
  }

  private Map<String, Object> approve(
      UUID postId,
      UUID tenantId,
      UUID clientId,
      Map<String, Object> post,
      String correlationId,
      String waMessageId) {
    posts.updateStatus(postId, PostStatus.publish_queued);

    String igUserId =
        posts.findIgUserId(tenantId).orElse("ig-" + tenantId.toString().substring(0, 8));
    String caption =
        post.get("caption") != null && !post.get("caption").toString().isBlank()
            ? post.get("caption").toString()
            : (post.get("instruction_text") == null
                ? "Posted with RozPost"
                : post.get("instruction_text").toString());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("postId", postId.toString());
    payload.put("igUserId", igUserId);
    payload.put("caption", caption);
    payload.put("mediaType", "IMAGE");
    payload.put("imageUrl", "r2://pending");
    payload.put("metaMode", metaMode);
    payload.put("fixturePermalink", fixturePermalink);
    payload.put("quotaTotal", quotaPerDay);
    payload.put("target", PublishTargetType.INSTAGRAM.name());
    payload.put("correlationId", correlationId);

    UUID jobId =
        jobs.enqueue(
            tenantId, clientId, "ig_publish", "ig-publish", "publish:" + postId, payload);

    events.publish(
        new Approved(
            tenantId,
            correlationId,
            waMessageId == null ? "approve-" + postId : waMessageId,
            postId.toString(),
            Instant.now()));

    log.info("Approved postId={} jobId={} correlationId={}", postId, jobId, correlationId);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("postId", postId.toString());
    out.put("status", PostStatus.publish_queued.name());
    out.put("jobId", jobId.toString());
    out.put("queue", "ig-publish");
    out.put("decision", ApprovalDecision.approved.name());
    out.put("target", PublishTargetType.INSTAGRAM.name());
    out.put("correlationId", correlationId);
    return out;
  }

  private Map<String, Object> disapprove(
      UUID postId,
      UUID tenantId,
      UUID clientId,
      Map<String, Object> post,
      String feedbackText,
      int retryCount,
      int maxRetries,
      String correlationId) {
    int next = retryCount + 1;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("postId", postId.toString());
    out.put("decision", ApprovalDecision.disapproved.name());
    out.put("retryCount", next);
    out.put("maxRetries", maxRetries);
    out.put("correlationId", correlationId);

    if (next >= maxRetries) {
      posts.updateAfterDisapprove(
          postId, feedbackText, next, PostStatus.abandoned_needs_new_media, null);
      events.publish(
          new Disapproved(tenantId, correlationId, postId, next, true, Instant.now()));
      out.put("status", PostStatus.abandoned_needs_new_media.name());
      out.put("manual", true);
      out.put("message", "Max 3 retries reached — needs new media (manual)");
      return out;
    }

    String caption = null;
    PostStatus nextStatus = PostStatus.media_processing;
    if ("mock".equalsIgnoreCase(metaMode) && mockAutoEnhance) {
      nextStatus = PostStatus.awaiting_approval;
      caption =
          "Retry "
              + next
              + " draft"
              + (feedbackText == null || feedbackText.isBlank() ? "" : " (" + feedbackText + ")");
      out.put("mockAutoEnhanced", true);
    }
    posts.updateAfterDisapprove(postId, feedbackText, next, nextStatus, caption);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("postId", postId.toString());
    payload.put("feedbackText", feedbackText == null ? "" : feedbackText);
    payload.put(
        "instructionText",
        post.get("instruction_text") == null ? "" : post.get("instruction_text").toString());
    payload.put("retryCount", next);
    payload.put("locale", "hi-IN");
    payload.put("correlationId", correlationId);

    UUID jobId =
        jobs.enqueue(
            tenantId,
            clientId,
            "image_enhance",
            "image-enhance",
            "enhance-retry:" + postId + ":" + next,
            payload);

    events.publish(new Disapproved(tenantId, correlationId, postId, next, false, Instant.now()));

    out.put("status", nextStatus.name());
    out.put("manual", false);
    out.put("jobId", jobId.toString());
    out.put("queue", "image-enhance");
    return out;
  }

  public PostSnapshot snapshot(UUID postId) {
    return posts
        .findById(postId)
        .map(PostSnapshot::from)
        .orElseThrow(() -> ApiException.notFound("post_not_found", "post not found: " + postId));
  }

  public record PostSnapshot(
      String id,
      String status,
      int retryCount,
      int maxRetries,
      String caption,
      String instructionText,
      String feedbackText,
      String igPermalink,
      String igMediaId) {
    static PostSnapshot from(Map<String, Object> m) {
      return new PostSnapshot(
          m.get("id").toString(),
          (String) m.get("status"),
          ((Number) m.get("retry_count")).intValue(),
          ((Number) m.get("max_retries")).intValue(),
          (String) m.get("caption"),
          (String) m.get("instruction_text"),
          (String) m.get("feedback_text"),
          (String) m.get("ig_permalink"),
          (String) m.get("ig_media_id"));
    }
  }
}

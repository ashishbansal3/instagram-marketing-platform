package com.rozpost.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rozpost.api.application.approval.ApprovalCommandService;
import com.rozpost.api.domain.port.JobEnqueuePort;
import com.rozpost.api.domain.port.PostRepositoryPort;
import com.rozpost.api.domain.event.Approved;
import com.rozpost.api.domain.event.DomainEvent;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.post.PostStatus;
import com.rozpost.api.interfaces.error.ApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprovalCommandServiceTest {
  private PostRepositoryPort posts;
  private JobEnqueuePort jobs;
  private DomainEventBus events;
  private ApprovalCommandService service;

  @BeforeEach
  void setUp() {
    posts = mock(PostRepositoryPort.class);
    jobs = mock(JobEnqueuePort.class);
    events = mock(DomainEventBus.class);
    service =
        new ApprovalCommandService(
            posts, jobs, events, "mock", "https://www.instagram.com/p/X/", 25, true);
  }

  @Test
  void approveEnqueuesPublishAndEmitsApproved() {
    UUID postId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    when(posts.findById(postId)).thenReturn(Optional.of(post(postId, tenantId, clientId, "awaiting_approval", 0)));
    when(posts.findClientWaUserId(clientId)).thenReturn("919876543210");
    when(posts.findIgUserId(tenantId)).thenReturn(Optional.of("ig-1"));
    when(jobs.enqueue(any(), any(), eq("ig_publish"), eq("ig-publish"), anyString(), anyMap()))
        .thenReturn(jobId);

    Map<String, Object> result =
        service.handleButton("919876543210", postId, "approve", null, "wamid.1");

    assertEquals("publish_queued", result.get("status"));
    assertEquals(jobId.toString(), result.get("jobId"));
    verify(posts).updateStatus(postId, PostStatus.publish_queued);
    ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
    verify(events).publish(cap.capture());
    assertEquals("Approved", cap.getValue().type());
    assertTrue(cap.getValue() instanceof Approved);
  }

  @Test
  void thirdDisapproveGoesManual() {
    UUID postId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(posts.findById(postId)).thenReturn(Optional.of(post(postId, tenantId, clientId, "awaiting_approval", 2)));
    when(posts.findClientWaUserId(clientId)).thenReturn("919876543210");

    Map<String, Object> result =
        service.handleButton("919876543210", postId, "disapprove", "bad colors", "wamid.2");

    assertEquals("abandoned_needs_new_media", result.get("status"));
    assertTrue((Boolean) result.get("manual"));
    verify(posts)
        .updateAfterDisapprove(
            eq(postId), eq("bad colors"), eq(3), eq(PostStatus.abandoned_needs_new_media), eq(null));
  }

  @Test
  void rejectsWrongStatus() {
    UUID postId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(posts.findById(postId)).thenReturn(Optional.of(post(postId, tenantId, clientId, "published", 0)));
    when(posts.findClientWaUserId(clientId)).thenReturn("919876543210");
    assertThrows(
        ApiException.class,
        () -> service.handleButton("919876543210", postId, "approve", null, null));
  }

  @Test
  void snapshotForTenantReturnsWhenTenantMatches() {
    UUID postId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(posts.findByIdAndTenantId(postId, tenantId))
        .thenReturn(Optional.of(post(postId, tenantId, clientId, "awaiting_approval", 0)));

    var snap = service.snapshotForTenant(postId, tenantId);
    assertEquals(postId.toString(), snap.id());
    assertEquals("awaiting_approval", snap.status());
  }

  @Test
  void snapshotForTenant404OnForeignTenant() {
    UUID postId = UUID.randomUUID();
    UUID ownerTenant = UUID.randomUUID();
    UUID attackerTenant = UUID.randomUUID();
    when(posts.findByIdAndTenantId(postId, attackerTenant)).thenReturn(Optional.empty());
    // Existence under another tenant must not be distinguishable from missing.
    when(posts.findById(postId))
        .thenReturn(Optional.of(post(postId, ownerTenant, UUID.randomUUID(), "awaiting_approval", 0)));

    ApiException ex =
        assertThrows(ApiException.class, () -> service.snapshotForTenant(postId, attackerTenant));
    assertEquals("post_not_found", ex.getCode());
    assertEquals(404, ex.getStatus().value());
  }

  @Test
  void snapshotForTenantRejectsNullTenant() {
    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.snapshotForTenant(UUID.randomUUID(), null));
    assertEquals("missing_tenant", ex.getCode());
    assertEquals(400, ex.getStatus().value());
  }

  private static Map<String, Object> post(
      UUID id, UUID tenantId, UUID clientId, String status, int retry) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", id);
    m.put("tenant_id", tenantId);
    m.put("client_id", clientId);
    m.put("status", status);
    m.put("caption", "cap");
    m.put("instruction_text", "instr");
    m.put("feedback_text", null);
    m.put("retry_count", retry);
    m.put("max_retries", 3);
    m.put("ig_permalink", null);
    m.put("ig_media_id", null);
    return m;
  }
}

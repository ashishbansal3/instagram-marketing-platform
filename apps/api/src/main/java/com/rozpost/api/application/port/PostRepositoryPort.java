package com.rozpost.api.application.port;

import com.rozpost.api.domain.post.PostStatus;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PostRepositoryPort {
  Optional<Map<String, Object>> findById(UUID postId);

  void updateStatus(UUID postId, PostStatus status);

  void updateAfterDisapprove(UUID postId, String feedback, int retryCount, PostStatus status, String caption);

  void recordApproval(
      UUID postId, UUID tenantId, String waMessageId, String decision, String note);

  Optional<String> findIgUserId(UUID tenantId);

  Optional<UUID> findLatestAwaitingApproval(String waUserId);

  String findClientWaUserId(UUID clientId);
}

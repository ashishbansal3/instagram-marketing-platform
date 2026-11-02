package com.rozpost.api.domain.post;

public enum PostStatus {
  intake_received,
  media_processing,
  drafted,
  awaiting_approval,
  publish_queued,
  publishing,
  published,
  publish_failed,
  abandoned_needs_new_media;

  public boolean canDecideApproval() {
    return this == awaiting_approval || this == drafted;
  }
}

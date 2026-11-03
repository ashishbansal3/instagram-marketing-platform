package com.rozpost.api.domain.port;

import java.util.Map;

/** Port for Instagram Content Publishing quota lookups (live Graph or META_MODE=mock fixtures). */
public interface MetaGraphClient {
    /**
     * {@code GET /{ig-user-id}/content_publishing_limit?fields=quota_usage,config}.
     * Never invents {@code quota_total} — fixture/live response is authoritative.
     */
    Map<String, Object> contentPublishingLimit(String igUserId);
}

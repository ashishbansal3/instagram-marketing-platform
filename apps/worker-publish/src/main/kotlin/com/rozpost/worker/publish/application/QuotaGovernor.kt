package com.rozpost.worker.publish.application

import com.rozpost.worker.publish.domain.QuotaSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Always trust live/fixture content_publishing_limit — never hardcode quota_total.
 * Used by [PublishJobProcessor] before every media_publish.
 */
@Component
class QuotaGovernor {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Decision(
        val allow: Boolean,
        val nearLimit: Boolean,
        val remaining: Long,
    )

    fun decide(snapshot: QuotaSnapshot, correlationId: String?): Decision {
        val remaining = (snapshot.total - snapshot.usage).coerceAtLeast(0)
        // Near-limit: one slot left (product headroom for ≤1 feed + 1 reel/day).
        val near = remaining == 1L
        if (remaining <= 0L) {
            log.warn(
                "quota_exhausted usage={} total={} correlationId={}",
                snapshot.usage,
                snapshot.total,
                correlationId,
            )
            return Decision(allow = false, nearLimit = false, remaining = 0)
        }
        if (near) {
            log.info(
                "quota_near_limit remaining={} usage={} total={} correlationId={}",
                remaining,
                snapshot.usage,
                snapshot.total,
                correlationId,
            )
        }
        return Decision(allow = true, nearLimit = near, remaining = remaining)
    }
}

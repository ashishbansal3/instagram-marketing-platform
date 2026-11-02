package com.rozpost.worker.publish.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class QuotaSnapshot(val usage: Int, val total: Int, val durationSeconds: Int) {
    val remaining: Int get() = (total - usage).coerceAtLeast(0)
    val nearLimit: Boolean get() = remaining <= 2
}

/**
 * Always trust live content_publishing_limit response — never hardcode quota_total.
 */
@Component
class QuotaGovernor {
    private val log = LoggerFactory.getLogger(javaClass)

    fun allowPublish(snapshot: QuotaSnapshot, correlationId: String): Boolean {
        if (snapshot.remaining <= 0) {
            log.warn("quota_exhausted usage={} total={} correlationId={}", snapshot.usage, snapshot.total, correlationId)
            return false
        }
        if (snapshot.nearLimit) {
            log.info("quota_near_limit remaining={} correlationId={}", snapshot.remaining, correlationId)
        }
        return true
    }
}

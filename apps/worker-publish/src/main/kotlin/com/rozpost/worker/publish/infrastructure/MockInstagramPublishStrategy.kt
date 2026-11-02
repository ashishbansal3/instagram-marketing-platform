package com.rozpost.worker.publish.infrastructure

import com.rozpost.worker.publish.domain.PublishContext
import com.rozpost.worker.publish.domain.PublishStrategy
import com.rozpost.worker.publish.domain.ScaffoldPublishResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["rozpost.meta-mode"], havingValue = "mock", matchIfMissing = true)
class MockInstagramPublishStrategy : PublishStrategy {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name: String = "instagram-mock"

    override fun publish(ctx: PublishContext): ScaffoldPublishResult {
        log.info("publish_mock postId={} correlationId={}", ctx.postId, ctx.correlationId)
        return ScaffoldPublishResult(
            mediaId = "IG_MEDIA_${ctx.postId}",
            permalink = "https://www.instagram.com/p/ROZPOST_${ctx.postId}/"
        )
    }
}

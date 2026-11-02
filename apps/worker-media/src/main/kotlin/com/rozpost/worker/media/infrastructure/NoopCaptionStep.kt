package com.rozpost.worker.media.infrastructure

import com.rozpost.worker.media.domain.MediaPipelineStep
import com.rozpost.worker.media.domain.MediaStepContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NoopCaptionStep : MediaPipelineStep {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name: String = "caption"
    override fun execute(ctx: MediaStepContext) {
        log.info("strategy_caption noop postId={} correlationId={}", ctx.postId, ctx.correlationId)
    }
}

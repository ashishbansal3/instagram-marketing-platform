package com.rozpost.worker.media.infrastructure

import com.rozpost.worker.media.domain.MediaPipelineStep
import com.rozpost.worker.media.domain.MediaStepContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NoopEnhanceStep : MediaPipelineStep {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name: String = "enhance"
    override fun execute(ctx: MediaStepContext) {
        log.info("strategy_enhance noop postId={} correlationId={}", ctx.postId, ctx.correlationId)
    }
}

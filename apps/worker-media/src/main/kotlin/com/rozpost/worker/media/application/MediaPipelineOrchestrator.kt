package com.rozpost.worker.media.application

import com.rozpost.worker.media.domain.MediaPipelineStep
import com.rozpost.worker.media.domain.MediaStepContext
import org.springframework.stereotype.Component

@Component
class MediaPipelineOrchestrator(
    private val steps: List<MediaPipelineStep>
) {
    fun stepNames(): List<String> = steps.map { it.name }

    fun run(ctx: MediaStepContext) {
        steps.forEach { it.execute(ctx) }
    }
}

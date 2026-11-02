package com.rozpost.worker.media.domain

interface MediaPipelineStep {
    val name: String
    fun execute(ctx: MediaStepContext)
}

data class MediaStepContext(
    val tenantId: String,
    val correlationId: String,
    val postId: String,
    val attributes: Map<String, String> = emptyMap()
)

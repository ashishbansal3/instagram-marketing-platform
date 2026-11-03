package com.rozpost.worker.publish.domain

/** Scaffolding Strategy for boot probes / checklist demos. Production publish uses PublishTargetStrategy. */
interface PublishStrategy {
    val name: String
    fun publish(ctx: PublishContext): ScaffoldPublishResult
}

data class PublishContext(
    val tenantId: String,
    val correlationId: String,
    val postId: String,
    val mediaUrl: String,
    val caption: String
)

data class ScaffoldPublishResult(val mediaId: String, val permalink: String)

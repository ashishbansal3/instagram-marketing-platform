package com.rozpost.worker.publish.domain

interface PublishStrategy {
    val name: String
    fun publish(ctx: PublishContext): PublishResult
}

data class PublishContext(
    val tenantId: String,
    val correlationId: String,
    val postId: String,
    val mediaUrl: String,
    val caption: String
)

data class PublishResult(val mediaId: String, val permalink: String)

package com.rozpost.worker.publish.domain

enum class PublishTargetType {
    INSTAGRAM,
    FACEBOOK,
    TIKTOK
}

data class PublishCommand(
    val jobId: String,
    val tenantId: String,
    val clientId: String?,
    val postId: String,
    val igUserId: String,
    val caption: String,
    val imageUrl: String,
    val metaMode: String,
    val fixturePermalink: String?,
    val quotaTotal: Int,
    val target: PublishTargetType,
    val correlationId: String?
)

data class PublishResult(
    val containerId: String,
    val mediaId: String,
    val permalink: String,
    val target: PublishTargetType
)

data class QuotaSnapshot(
    val usage: Long,
    val total: Long
)

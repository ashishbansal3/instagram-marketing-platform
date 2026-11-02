package com.rozpost.worker.media.domain.event

import java.time.Instant

sealed interface DomainEvent {
    val eventId: String
    val occurredAt: Instant
    val correlationId: String?
    val tenantId: String
    val type: String
}

data class MediaProcessed(
    override val eventId: String,
    override val occurredAt: Instant = Instant.now(),
    override val correlationId: String?,
    override val tenantId: String,
    val postId: String,
    val jobId: String,
    val status: String,
    val engine: String?,
    val previewUrls: Map<String, String>,
    val instructionText: String?,
) : DomainEvent {
    override val type: String = "MediaProcessed"
}

data class MediaProcessingFailed(
    override val eventId: String,
    override val occurredAt: Instant = Instant.now(),
    override val correlationId: String?,
    override val tenantId: String,
    val postId: String,
    val jobId: String,
    val queue: String,
    val error: String,
) : DomainEvent {
    override val type: String = "MediaProcessingFailed"
}

fun interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}

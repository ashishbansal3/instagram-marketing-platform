package com.rozpost.worker.media.domain.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class JobEnvelope(
    val jobId: String? = null,
    val tenantId: String? = null,
    val clientId: String? = null,
    val type: String? = null,
    val idempotencyKey: String? = null,
    val createdAt: String? = null,
    val correlationId: String? = null,
    val payload: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MediaDownloadJob(
    val jobId: String,
    val tenantId: String,
    val postId: String,
    val sourceUrl: String? = null,
    val localPath: String? = null,
    val waMediaId: String? = null,
    val instructionText: String? = null,
    val locale: String = "hi-IN",
    val kind: String = "image",
    val idempotencyKey: String? = null,
    val correlationId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageEnhanceJob(
    val jobId: String,
    val tenantId: String,
    val postId: String,
    val assetKey: String,
    val instructionText: String? = null,
    val locale: String = "hi-IN",
    val brandName: String? = null,
    val idempotencyKey: String? = null,
    val correlationId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReelRenderJob(
    val jobId: String,
    val tenantId: String,
    val postId: String,
    val assetKey: String,
    val instructionText: String? = null,
    val caption: String? = null,
    val locale: String = "hi-IN",
    val brandName: String? = null,
    val durationSeconds: Int = 5,
    val idempotencyKey: String? = null,
    val correlationId: String? = null,
)

data class PipelineResult(
    val jobId: String,
    val tenantId: String,
    val postId: String,
    val status: String,
    val correlationId: String? = null,
    val originalKey: String? = null,
    val enhancedKey: String? = null,
    val reelKey: String? = null,
    val previewUrls: Map<String, String> = emptyMap(),
    val engine: String? = null,
    val instructionText: String? = null,
    val message: String? = null,
)

data class EnhanceResult(
    val outputPath: java.nio.file.Path,
    val provider: String,
)

data class CaptionResult(
    val outputPath: java.nio.file.Path,
    val provider: String,
    val previewUrl: String? = null,
)

package com.rozpost.worker.media.domain.error

sealed class MediaException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : RuntimeException(message, cause)

class ProviderUnavailableException(provider: String, cause: Throwable? = null) :
    MediaException("Provider unavailable: $provider", cause, retryable = true)

class ProviderCircuitOpenException(provider: String) :
    MediaException("Circuit open for provider: $provider", retryable = true)

class MediaValidationException(message: String) :
    MediaException(message, retryable = false)

class MediaProcessingException(message: String, cause: Throwable? = null, retryable: Boolean = true) :
    MediaException(message, cause, retryable)

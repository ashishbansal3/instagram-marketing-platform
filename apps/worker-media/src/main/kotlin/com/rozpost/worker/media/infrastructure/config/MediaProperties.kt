package com.rozpost.worker.media.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media")
data class MediaProperties(
    val workDir: String = "/tmp/media-work",
    val queues: Queues = Queues(),
    val consumer: Consumer = Consumer(),
    val s3: S3 = S3(),
    val enhance: Enhance = Enhance(),
    val remotion: Remotion = Remotion(),
    val notify: Notify = Notify(),
    val resilience: Resilience = Resilience(),
) {
    data class Queues(
        /** Redis Stream names (consumer groups). */
        val download: String = "stream.media-download",
        val enhance: String = "stream.image-enhance",
        val reel: String = "stream.reel-render",
        val dlq: String = "stream.media-dlq",
        /** Legacy list keys (API LPUSH) bridged into streams. */
        val downloadList: String = "media-download",
        val enhanceList: String = "image-enhance",
        val reelList: String = "reel-render",
    )

    data class Consumer(
        val enabled: Boolean = true,
        val pollMs: Long = 1000,
        val maxAttempts: Long = 5,
    )

    data class S3(
        val endpoint: String = "http://localhost:9000",
        val region: String = "us-east-1",
        val bucket: String = "imp-media",
        val accessKey: String = "minioadmin",
        val secretKey: String = "minioadmin",
        val publicBaseUrl: String = "http://localhost:9000/imp-media",
        val pathStyle: Boolean = true,
    )

    data class Enhance(
        /** When false, paid/mock providers gated off except live keys. */
        val allowMockFallback: Boolean = true,
        val photoroomApiKey: String = "",
        val falApiKey: String = "",
        /** Reserved for future Kling I2V provider registration. */
        val klingApiKey: String = "",
    )

    data class Remotion(
        val baseUrl: String = "http://localhost:3100",
    )

    data class Notify(
        val apiBaseUrl: String = "http://localhost:8080",
        val waMockEnabled: Boolean = false,
    )

    data class Resilience(
        val failureThreshold: Int = 3,
        val openDurationMs: Long = 60_000,
    )
}

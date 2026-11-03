package com.rozpost.worker.media.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media")
data class MediaProperties(
    val workDir: String = "/tmp/media-work",
    val queues: Queues = Queues(),
    val consumer: Consumer = Consumer(),
    val s3: S3 = S3(),
    val whatsapp: WhatsApp = WhatsApp(),
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
        /** Idle PEL entries older than this are XAUTOCLAIM'd from dead consumers. */
        val pelMinIdleMs: Long = 30_000,
        /** Max messages to reclaim per poll. */
        val pelClaimCount: Int = 10,
    )

    data class S3(
        val endpoint: String = "http://localhost:9000",
        val region: String = "us-east-1",
        val bucket: String = "rozpost-media",
        val accessKey: String = "minioadmin",
        val secretKey: String = "minioadmin",
        val publicBaseUrl: String = "http://localhost:9000/rozpost-media",
        val pathStyle: Boolean = true,
    )

    data class WhatsApp(
        /** mock | live — live fetches Graph media URL; mock uses fixture/local stub. */
        val metaMode: String = System.getenv("META_MODE") ?: "mock",
        val graphVersion: String = System.getenv("META_GRAPH_VERSION") ?: "v21.0",
        val accessToken: String = System.getenv("META_WA_ACCESS_TOKEN") ?: "",
        val mockMediaPath: String = System.getenv("MOCK_MEDIA_PATH") ?: "/app/fixtures/sample-salon.jpg",
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

package com.rozpost.worker.publish.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "publish")
data class PublishProperties(
    val queues: Queues = Queues(),
    val consumer: Consumer = Consumer(),
) {
    data class Queues(
        /** Redis Stream name (consumer group). */
        val publish: String = "stream.ig-publish",
        val delayed: String = "stream.ig-publish-delayed",
        val dlq: String = "stream.ig-publish-dlq",
        /** Legacy list key (API LPUSH) bridged into the stream. */
        val publishList: String = "ig-publish",
        val delayedList: String = "ig-publish-delayed",
    )

    data class Consumer(
        val enabled: Boolean = true,
        val pollMs: Long = 1000,
        val maxAttempts: Long = 5,
    )
}

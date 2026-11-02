package com.rozpost.worker.media

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class MediaWorkerApp

fun main(args: Array<String>) {
    runApplication<MediaWorkerApp>(*args)
}

@RestController
class HealthController(
    @Value("${rozpost.meta-mode:mock}") private val metaMode: String
) {
    @GetMapping("/api/health")
    fun health() = mapOf("service" to "rozpost-worker-media", "ok" to true, "metaMode" to metaMode)
}

@Component
class StreamConsumer(
    private val redis: StringRedisTemplate,
    @Value("${rozpost.meta-mode:mock}") private val metaMode: String,
    @Value("${rozpost.remotion-url:http://localhost:3100}") private val remotionUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        log.info("RozPost worker-media ready metaMode={} stream=media-download remotion={}", metaMode, remotionUrl)
        // Skeleton: one non-blocking peek so compose boots cleanly; full consumer loop lands next.
        try {
            val records: List<MapRecord<String, Any, Any>> =
                redis.opsForStream<Any, Any>().read(StreamOffset.latest("media-download")) ?: emptyList()
            log.info("stream={} peeked {}", "media-download", records.size)
        } catch (ex: Exception) {
            log.warn("stream {} not ready yet: {}", "media-download", ex.message)
        }
    }
}

package com.rozpost.worker.publish

import com.rozpost.worker.publish.application.PublishJobProcessor
import com.rozpost.worker.publish.domain.PublishStrategy
import com.rozpost.worker.publish.infrastructure.RedisStreamPeek
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@EnableScheduling
class PublishWorkerApp

fun main(args: Array<String>) {
    runApplication<PublishWorkerApp>(*args)
}

@RestController
class HealthController(
    @Value("\${rozpost.meta-mode:mock}") private val metaMode: String,
    @Value("\${rozpost.app-env:local}") private val appEnv: String
) {
    @GetMapping("/api/v1/health")
    fun health() = mapOf(
        "service" to "rozpost-worker-publish",
        "ok" to true,
        "metaMode" to metaMode,
        "appEnv" to appEnv,
        "queue" to "ig-publish"
    )
}

@Component
class BootProbe(
    private val peek: RedisStreamPeek,
    private val strategies: List<PublishStrategy>,
    @Value("\${rozpost.meta-mode:mock}") private val metaMode: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        MDC.put("correlationId", "boot-publish-worker")
        try {
            log.info("worker_publish_ready metaMode={} strategies={}", metaMode, strategies.map { it.name })
            peek.peek("ig-publish")
        } finally {
            MDC.remove("correlationId")
        }
    }
}

@Component
class IgPublishConsumer(
    private val redis: StringRedisTemplate,
    private val jdbc: JdbcTemplate,
    private val processor: PublishJobProcessor,
    @Value("\${rozpost.meta-mode:mock}") private val metaMode: String,
    @Value("\${rozpost.publish-quota-per-day:25}") private val quotaPerDay: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        log.info("ig-publish consumer ready metaMode={} quota~/day={}", metaMode, quotaPerDay)
    }

    @Scheduled(fixedDelayString = "\${rozpost.publish-poll-ms:1000}")
    fun poll() {
        try {
            val json = redis.opsForList().rightPop("ig-publish")
            if (json != null) {
                processor.processEnvelope(json)
                return
            }
            val pending = jdbc.query(
                """
                SELECT payload::text FROM jobs
                WHERE type = 'ig_publish' AND status = 'queued'
                ORDER BY created_at ASC LIMIT 5
                """.trimIndent()
            ) { rs, _ -> rs.getString(1) }
            pending.forEach { processor.processEnvelope(it) }
        } catch (ex: Exception) {
            log.warn("ig-publish poll error: {}", ex.message)
        }
    }
}

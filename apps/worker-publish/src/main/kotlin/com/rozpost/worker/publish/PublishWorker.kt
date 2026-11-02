package com.rozpost.worker.publish

import com.rozpost.worker.publish.domain.PublishStrategy
import com.rozpost.worker.publish.infrastructure.RedisStreamPeek
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
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
        "appEnv" to appEnv
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

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
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableScheduling
class PublishWorkerApp

fun main(args: Array<String>) {
    runApplication<PublishWorkerApp>(*args)
}

@Component
class BootProbe(
    private val peek: RedisStreamPeek,
    private val strategies: List<PublishStrategy>,
    @Value("\${rozpost.meta-mode:mock}") private val metaMode: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        MDC.put("correlationId", "boot-publish-worker")
        try {
            log.info("worker_publish_ready metaMode={} strategies={}", metaMode, strategies.map { it.name })
            peek.peek("stream.ig-publish")
        } finally {
            MDC.remove("correlationId")
        }
    }
}

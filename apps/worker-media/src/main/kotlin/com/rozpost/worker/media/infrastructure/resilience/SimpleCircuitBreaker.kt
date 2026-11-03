package com.rozpost.worker.media.infrastructure.resilience

import com.rozpost.worker.media.domain.error.ProviderCircuitOpenException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight per-provider circuit breaker (closed → open → half-open).
 * Enough for media vendor HTTP without bringing Resilience4j into the MVP classpath.
 */
class SimpleCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val openDurationMs: Long = 60_000,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val failures = ConcurrentHashMap<String, AtomicInteger>()
    private val openedAt = ConcurrentHashMap<String, Long>()

    fun <T> execute(name: String, block: () -> T): T {
        val opened = openedAt[name]
        if (opened != null) {
            val elapsed = Instant.now().toEpochMilli() - opened
            if (elapsed < openDurationMs) {
                throw ProviderCircuitOpenException(name)
            }
            // half-open probe
            openedAt.remove(name)
            log.info("circuit half-open provider={}", name)
        }
        return try {
            val result = block()
            failures[name]?.set(0)
            result
        } catch (ex: Exception) {
            val count = failures.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet()
            if (count >= failureThreshold) {
                openedAt[name] = Instant.now().toEpochMilli()
                log.warn("circuit OPEN provider={} failures={}", name, count)
            }
            throw ex
        }
    }
}

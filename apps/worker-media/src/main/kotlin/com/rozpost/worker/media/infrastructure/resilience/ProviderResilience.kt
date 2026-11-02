package com.rozpost.worker.media.infrastructure.resilience

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.function.Supplier

/**
 * One CB + retry + bulkhead instance per remote backend (Photoroom, fal, Remotion, …).
 * CircuitBreaker wraps Retry (execute CB around Retry supplier).
 */
@Component
class ProviderResilience {
    private val cbRegistry = CircuitBreakerRegistry.of(
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(3)
            .build(),
    )
    private val retryRegistry = RetryRegistry.of(
        RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(200))
            .retryOnException { ex ->
                val msg = ex.message?.lowercase() ?: ""
                // never retry model/safety rejects
                !msg.contains("safety") && !msg.contains("content policy") && !msg.contains("moderation")
            }
            .build(),
    )
    private val bulkheadRegistry = BulkheadRegistry.of(
        BulkheadConfig.custom()
            .maxConcurrentCalls(4)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build(),
    )

    fun <T> call(backend: String, block: () -> T): T {
        val cb = cbRegistry.circuitBreaker(backend)
        val retry = retryRegistry.retry(backend)
        val bulkhead = bulkheadRegistry.bulkhead(backend)
        val supplier = Supplier { block() }
        val withRetry = Retry.decorateSupplier(retry, supplier)
        val withCb = CircuitBreaker.decorateSupplier(cb, withRetry)
        val withBh = Bulkhead.decorateSupplier(bulkhead, withCb)
        return withBh.get()
    }

    fun circuitBreaker(backend: String): CircuitBreaker = cbRegistry.circuitBreaker(backend)
}

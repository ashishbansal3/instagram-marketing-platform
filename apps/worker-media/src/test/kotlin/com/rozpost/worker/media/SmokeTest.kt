package com.rozpost.worker.media

import com.rozpost.worker.media.domain.strategy.CaptionStrategy
import com.rozpost.worker.media.domain.strategy.CutStrategy
import com.rozpost.worker.media.domain.strategy.EnhanceStrategy
import com.rozpost.worker.media.infrastructure.resilience.SimpleCircuitBreaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmokeTest {
    @Test
    fun applicationClassIsRozpost() {
        assertTrue(WorkerMediaApplication::class.java.name.contains("rozpost"))
    }

    @Test
    fun strategyInterfacesExistForOpenClosed() {
        assertTrue(EnhanceStrategy::class.java.isInterface)
        assertTrue(CaptionStrategy::class.java.isInterface)
        assertTrue(CutStrategy::class.java.isInterface)
    }

    @Test
    fun circuitBreakerOpensAfterThreshold() {
        val cb = SimpleCircuitBreaker(failureThreshold = 2, openDurationMs = 60_000)
        repeat(2) {
            assertThrows(RuntimeException::class.java) {
                cb.execute("prov") { error("boom") }
            }
        }
        val ex = assertThrows(com.rozpost.worker.media.domain.error.ProviderCircuitOpenException::class.java) {
            cb.execute("prov") { "ok" }
        }
        assertEquals("prov", ex.message?.substringAfter(": ")?.trim() ?: "prov")
    }
}

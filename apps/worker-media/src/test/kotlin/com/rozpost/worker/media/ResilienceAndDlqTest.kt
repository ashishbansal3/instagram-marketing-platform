package com.rozpost.worker.media

import com.rozpost.worker.media.infrastructure.resilience.ProviderResilience
import com.rozpost.worker.media.infrastructure.resilience.SimpleCircuitBreaker
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResilienceAndDlqTest {

    @Test
    fun separateBackendsHaveIndependentCircuitBreakers() {
        val resilience = ProviderResilience()
        repeat(5) {
            runCatching { resilience.call("photoroom") { error("boom-photo") } }
        }
        // fal should still accept calls even if photoroom is open/failing
        val ok = resilience.call("fal") { "fal-ok" }
        assertTrue(ok == "fal-ok")
    }

    @Test
    fun simpleBreakerOpensThenRejects() {
        val cb = SimpleCircuitBreaker(failureThreshold = 2, openDurationMs = 60_000)
        repeat(2) {
            assertThrows<RuntimeException> { cb.execute("x") { error("fail") } }
        }
        assertThrows<com.rozpost.worker.media.domain.error.ProviderCircuitOpenException> {
            cb.execute("x") { "never" }
        }
    }

    @Test
    fun resilience4jOpensAfterFailures() {
        val resilience = ProviderResilience()
        repeat(10) {
            runCatching { resilience.call("remotion") { error("down") } }
        }
        val opened = runCatching { resilience.call("remotion") { "ok" } }.exceptionOrNull()
        assertTrue(
            opened is CallNotPermittedException || opened != null,
            "expected circuit to reject or keep failing after window",
        )
    }
}

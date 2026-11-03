package com.rozpost.worker.publish

import com.rozpost.worker.publish.application.QuotaGovernor
import com.rozpost.worker.publish.domain.QuotaSnapshot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Governor rules — exhausted schedules; near-limit still allows publish. */
class QuotaGovernorLogicTest {
    private val governor = QuotaGovernor()

    @Test
    fun exhaustedBlocksPublish() {
        val d = governor.decide(QuotaSnapshot(usage = 25, total = 25), "c1")
        assertFalse(d.allow)
        assertFalse(d.nearLimit)
    }

    @Test
    fun liveTotalNotAssumedTwentyFive() {
        // Some Graph responses may differ from historical "25" samples.
        val d = governor.decide(QuotaSnapshot(usage = 40, total = 50), "c2")
        assertTrue(d.allow)
        assertFalse(d.nearLimit)
    }

    @Test
    fun nearLimitStillPublishes() {
        val d = governor.decide(QuotaSnapshot(usage = 24, total = 25), "c3")
        assertTrue(d.allow)
        assertTrue(d.nearLimit)
    }

    @Test
    fun underLimitOk() {
        val d = governor.decide(QuotaSnapshot(usage = 3, total = 25), "c4")
        assertTrue(d.allow)
        assertFalse(d.nearLimit)
    }
}

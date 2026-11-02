package com.rozpost.worker.publish

import com.rozpost.worker.publish.domain.QuotaSnapshot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure governor rules — exhausted schedules; near-limit still allows publish. */
class QuotaGovernorLogicTest {
    private fun canPublish(q: QuotaSnapshot): Boolean = q.usage < q.total

    private fun nearLimit(q: QuotaSnapshot): Boolean = q.total - q.usage <= 1 && canPublish(q)

    @Test
    fun exhaustedBlocksPublish() {
        val q = QuotaSnapshot(usage = 25, total = 25)
        assertFalse(canPublish(q))
        assertFalse(nearLimit(q))
    }

    @Test
    fun liveTotalNotAssumedTwentyFive() {
        // Some Graph responses may differ from historical "25" samples.
        val q = QuotaSnapshot(usage = 40, total = 50)
        assertTrue(canPublish(q))
        assertFalse(nearLimit(q))
    }

    @Test
    fun nearLimitStillPublishes() {
        val q = QuotaSnapshot(usage = 24, total = 25)
        assertTrue(canPublish(q))
        assertTrue(nearLimit(q))
    }

    @Test
    fun underLimitOk() {
        val q = QuotaSnapshot(usage = 3, total = 25)
        assertTrue(canPublish(q))
        assertFalse(nearLimit(q))
    }
}

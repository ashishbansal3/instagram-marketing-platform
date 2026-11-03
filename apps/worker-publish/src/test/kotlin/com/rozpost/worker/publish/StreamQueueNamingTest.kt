package com.rozpost.worker.publish

import com.rozpost.worker.publish.infrastructure.config.PublishProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class StreamQueueNamingTest {
    @Test
    fun streamAndListKeysAreDistinct() {
        val q = PublishProperties.Queues()
        assertEquals("stream.ig-publish", q.publish)
        assertEquals("ig-publish", q.publishList)
        assertEquals("stream.ig-publish-dlq", q.dlq)
        assertFalse(q.publish == q.publishList)
    }
}

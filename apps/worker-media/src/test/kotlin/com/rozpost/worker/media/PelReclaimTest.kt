package com.rozpost.worker.media

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.media.infrastructure.config.MediaProperties
import com.rozpost.worker.media.infrastructure.queue.StreamJobQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.PendingMessages
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class PelReclaimTest {

    @Test
    fun consumerDefaultsExposePelReclaimSettings() {
        val c = MediaProperties.Consumer()
        assertEquals(30_000L, c.pelMinIdleMs)
        assertEquals(10, c.pelClaimCount)
        assertEquals(5L, c.maxAttempts)
    }

    @Test
    fun reclaimIdleClaimsForeignIdleMessagesViaXclaim() {
        val redis = mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val streamOps = mock(StreamOperations::class.java) as StreamOperations<String, String, String>
        `when`(redis.opsForStream<String, String>()).thenReturn(streamOps)
        `when`(streamOps.createGroup(anyString(), any(), anyString()))
            .thenThrow(RuntimeException("BUSYGROUP"))

        val foreign = PendingMessage(
            RecordId.of("1-0"),
            Consumer.from("worker-media", "dead-worker-1"),
            Duration.ofMinutes(2),
            3L,
        )
        val ours = PendingMessage(
            RecordId.of("2-0"),
            Consumer.from("worker-media", "this-same-name-will-not-match-pid"),
            Duration.ofMinutes(2),
            1L,
        )
        // "ours" still has a different consumer name than the live worker (pid-based), so both
        // would be claimable — verify claim is invoked with the idle foreign id at minimum.
        val pending = PendingMessages(
            "worker-media",
            Range.unbounded<String>(),
            listOf(foreign, ours),
        )
        `when`(
            streamOps.pending(
                eq("stream.media-download"),
                eq("worker-media"),
                any(),
                anyLong(),
            ),
        ).thenReturn(pending)

        val claimed = MapRecord.create(
            "stream.media-download",
            mapOf("payload" to """{"jobId":"j1","tenantId":"t1"}"""),
        ).withId(RecordId.of("1-0"))
        `when`(
            streamOps.claim(
                eq("stream.media-download"),
                eq("worker-media"),
                anyString(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(listOf(claimed))

        // deliveryCount lookup for claimed id
        `when`(
            streamOps.pending(
                eq("stream.media-download"),
                eq("worker-media"),
                any(),
                eq(1L),
            ),
        ).thenReturn(
            PendingMessages(
                "worker-media",
                listOf(
                    PendingMessage(
                        RecordId.of("1-0"),
                        Consumer.from("worker-media", "dead-worker-1"),
                        Duration.ofMinutes(2),
                        3L,
                    ),
                ),
            ),
        )

        val queue = StreamJobQueue(redis, ObjectMapper(), MediaProperties())
        val out = queue.reclaimIdle("stream.media-download", 5)

        assertEquals(1, out.size)
        assertEquals("1-0", out[0].id)
        assertTrue(out[0].payload.contains("j1"))
        assertEquals(3L, out[0].deliveryCount)
    }

    @Test
    fun reclaimIdleSkipsWhenNothingIdle() {
        val redis = mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val streamOps = mock(StreamOperations::class.java) as StreamOperations<String, String, String>
        `when`(redis.opsForStream<String, String>()).thenReturn(streamOps)
        `when`(streamOps.createGroup(anyString(), any(), anyString()))
            .thenThrow(RuntimeException("BUSYGROUP"))
        `when`(
            streamOps.pending(
                eq("stream.media-download"),
                eq("worker-media"),
                any(),
                anyLong(),
            ),
        ).thenReturn(PendingMessages("worker-media", emptyList()))

        val queue = StreamJobQueue(redis, ObjectMapper(), MediaProperties())
        val out = queue.reclaimIdle("stream.media-download", 5)
        assertTrue(out.isEmpty())
        verify(streamOps, never()).claim(
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
        )
    }

    @Test
    fun reclaimIdleDisabledWhenMinIdleZero() {
        val redis = mock(StringRedisTemplate::class.java)
        val props = MediaProperties(consumer = MediaProperties.Consumer(pelMinIdleMs = 0))
        val queue = StreamJobQueue(redis, ObjectMapper(), props)
        assertTrue(queue.reclaimIdle("stream.media-download").isEmpty())
    }
}

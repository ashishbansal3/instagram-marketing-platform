package com.rozpost.worker.publish

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.publish.infrastructure.config.PublishProperties
import com.rozpost.worker.publish.infrastructure.queue.StreamJobQueue
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
        val c = PublishProperties.Consumer()
        assertEquals(30_000L, c.pelMinIdleMs)
        assertEquals(10, c.pelClaimCount)
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
            RecordId.of("9-0"),
            Consumer.from("worker-publish", "dead-publish-1"),
            Duration.ofMinutes(5),
            4L,
        )
        val pending = PendingMessages(
            "worker-publish",
            Range.unbounded<String>(),
            listOf(foreign),
        )
        `when`(
            streamOps.pending(
                eq("stream.ig-publish"),
                eq("worker-publish"),
                any(),
                anyLong(),
            ),
        ).thenReturn(pending)

        val claimed = MapRecord.create(
            "stream.ig-publish",
            mapOf("payload" to """{"jobId":"pub-1","tenantId":"t1"}"""),
        ).withId(RecordId.of("9-0"))
        `when`(
            streamOps.claim(
                eq("stream.ig-publish"),
                eq("worker-publish"),
                anyString(),
                any(),
                any(),
            ),
        ).thenReturn(listOf(claimed))

        `when`(
            streamOps.pending(
                eq("stream.ig-publish"),
                eq("worker-publish"),
                any(),
                eq(1L),
            ),
        ).thenReturn(
            PendingMessages(
                "worker-publish",
                listOf(
                    PendingMessage(
                        RecordId.of("9-0"),
                        Consumer.from("worker-publish", "dead-publish-1"),
                        Duration.ofMinutes(5),
                        4L,
                    ),
                ),
            ),
        )

        val queue = StreamJobQueue(redis, ObjectMapper(), PublishProperties())
        val out = queue.reclaimIdle("stream.ig-publish", 5)

        assertEquals(1, out.size)
        assertEquals("9-0", out[0].id)
        assertTrue(out[0].payload.contains("pub-1"))
        assertEquals(4L, out[0].deliveryCount)
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
                eq("stream.ig-publish"),
                eq("worker-publish"),
                any(),
                anyLong(),
            ),
        ).thenReturn(PendingMessages("worker-publish", emptyList()))

        val queue = StreamJobQueue(redis, ObjectMapper(), PublishProperties())
        assertTrue(queue.reclaimIdle("stream.ig-publish", 5).isEmpty())
        verify(streamOps, never()).claim(
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
        )
    }
}

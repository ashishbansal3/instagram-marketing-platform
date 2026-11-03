package com.rozpost.worker.media.infrastructure.queue

import com.rozpost.worker.media.infrastructure.config.MediaProperties
import org.springframework.stereotype.Component

/** Facade used by application layer — Redis Streams under the hood. */
@Component
class JobQueue(
    private val streams: StreamJobQueue,
    private val props: MediaProperties,
) {
    fun enqueueDownload(payload: Any) = streams.enqueueDownload(payload)
    fun enqueueEnhance(payload: Any) = streams.enqueueEnhance(payload)
    fun enqueueReel(payload: Any) = streams.enqueueReel(payload)
    fun depth(queue: String): Long = streams.depthApprox(queue)
    fun dlqDepth(): Long = streams.depthApprox(props.queues.dlq)
}

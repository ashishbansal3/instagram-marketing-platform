package com.rozpost.worker.media.infrastructure.schedule

import com.rozpost.worker.media.infrastructure.config.MediaProperties
import com.rozpost.worker.media.infrastructure.queue.JobQueue
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["shedlock.enabled"], havingValue = "true", matchIfMissing = true)
class QueueHeartbeatJob(
    private val queue: JobQueue,
    private val props: MediaProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${media.shedlock.heartbeat-ms:60000}")
    @SchedulerLock(name = "mediaQueueHeartbeat", lockAtLeastFor = "PT50S", lockAtMostFor = "PT2M")
    fun heartbeat() {
        log.info(
            "queue depths download={} enhance={} reel={} dlq={}",
            queue.depth(props.queues.download),
            queue.depth(props.queues.enhance),
            queue.depth(props.queues.reel),
            queue.depth(props.queues.dlq),
        )
    }
}

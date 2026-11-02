package com.rozpost.worker.publish.infrastructure.strategy

import com.rozpost.worker.publish.domain.PublishCommand
import com.rozpost.worker.publish.domain.PublishResult
import com.rozpost.worker.publish.domain.PublishTargetType
import com.rozpost.worker.publish.domain.QuotaSnapshot

interface PublishTargetStrategy {
    val target: PublishTargetType
    fun quota(command: PublishCommand): QuotaSnapshot
    fun publish(command: PublishCommand): PublishResult
}

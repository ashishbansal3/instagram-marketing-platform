package com.rozpost.worker.publish.interfaces

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    @Value("\${rozpost.meta-mode:mock}") private val metaMode: String,
    @Value("\${rozpost.app-env:local}") private val appEnv: String,
) {
    @GetMapping("/api/v1/health")
    fun health() = mapOf(
        "service" to "rozpost-worker-publish",
        "ok" to true,
        "metaMode" to metaMode,
        "appEnv" to appEnv,
        "queue" to "stream.ig-publish",
        "queueListBridge" to "ig-publish",
    )
}

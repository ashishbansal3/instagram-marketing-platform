package com.rozpost.worker.media.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisStreamPeek(private val redis: StringRedisTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun peek(stream: String) {
        try {
            val records = redis.opsForStream<Any, Any>().read(StreamOffset.latest(stream)) ?: emptyList()
            log.info("stream={} peeked {}", stream, records.size)
        } catch (ex: Exception) {
            log.warn("stream {} not ready yet: {}", stream, ex.message)
        }
    }
}

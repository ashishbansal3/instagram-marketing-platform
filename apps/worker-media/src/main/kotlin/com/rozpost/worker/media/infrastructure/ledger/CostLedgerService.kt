package com.rozpost.worker.media.infrastructure.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Lightweight cost ledger rows for COGS (image_api / reel_render / storage).
 * Durable Postgres table lands with shared Flyway; Redis list is the interim sink.
 */
@Service
class CostLedgerService(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val key = "cost-ledger"

    fun record(
        tenantId: String,
        jobId: String?,
        category: String,
        amountInr: BigDecimal,
        meta: Map<String, Any?> = emptyMap(),
    ) {
        val row = mapOf(
            "id" to UUID.randomUUID().toString(),
            "tenantId" to tenantId,
            "jobId" to jobId,
            "category" to category,
            "amountInr" to amountInr,
            "meta" to meta,
            "correlationId" to MDC.get("correlationId"),
            "createdAt" to Instant.now().toString(),
        )
        redis.opsForList().leftPush(key, mapper.writeValueAsString(row))
        log.info(
            "cost_ledger category={} amountInr={} tenantId={} jobId={} correlationId={}",
            category, amountInr, tenantId, jobId, MDC.get("correlationId"),
        )
    }
}

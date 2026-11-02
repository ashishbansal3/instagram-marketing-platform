package com.rozpost.worker.publish.infrastructure.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Component

@Component
class TenantPlanGate(private val jdbc: JdbcTemplate) {
    class PlanBlockedException(message: String) : RuntimeException(message)

    fun assertPublishAllowed(tenantId: String) {
        val status = jdbc.query(
            "SELECT status, plan FROM tenants WHERE id = ?::uuid",
            ResultSetExtractor { rs ->
                if (rs.next()) rs.getString("status") to rs.getString("plan") else null
            },
            tenantId
        ) ?: throw PlanBlockedException("tenant not found: $tenantId")

        val (tenantStatus, _) = status
        if (tenantStatus == "past_due" || tenantStatus == "suspended") {
            throw PlanBlockedException("tenant status=$tenantStatus blocks publish")
        }
    }
}

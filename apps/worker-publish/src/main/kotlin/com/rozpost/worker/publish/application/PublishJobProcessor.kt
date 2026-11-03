package com.rozpost.worker.publish.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.publish.domain.PublishCommand
import com.rozpost.worker.publish.domain.PublishTargetType
import com.rozpost.worker.publish.infrastructure.meta.MetaTokenVault
import com.rozpost.worker.publish.infrastructure.persistence.TenantPlanGate
import com.rozpost.worker.publish.infrastructure.queue.StreamJobQueue
import com.rozpost.worker.publish.infrastructure.strategy.PublishStrategyRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Service

@Service
class PublishJobProcessor(
    private val jdbc: JdbcTemplate,
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val strategies: PublishStrategyRegistry,
    private val planGate: TenantPlanGate,
    private val tokens: MetaTokenVault,
    private val quotaGovernor: QuotaGovernor,
    private val streams: StreamJobQueue,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processEnvelope(json: String) {
        val root: JsonNode = mapper.readTree(json)
        val command = toCommand(root)

        val jobStatus = jdbc.query(
            "SELECT status FROM jobs WHERE id = ?::uuid",
            ResultSetExtractor { rs -> if (rs.next()) rs.getString(1) else null },
            command.jobId
        )
        if (jobStatus == "succeeded") {
            log.info("Skip duplicate ig_publish jobId={} already succeeded", command.jobId)
            return
        }

        // Idempotent short-circuit: post already published → emit nothing twice.
        data class PostSnap(val status: String?, val mediaId: String?, val permalink: String?)
        val postRow = jdbc.query(
            "SELECT status, ig_media_id, ig_permalink FROM posts WHERE id = ?::uuid",
            ResultSetExtractor { rs ->
                if (rs.next()) {
                    PostSnap(rs.getString("status"), rs.getString("ig_media_id"), rs.getString("ig_permalink"))
                } else null
            },
            command.postId
        )
        if (postRow?.status == "published" && !postRow.mediaId.isNullOrBlank()) {
            jdbc.update(
                "UPDATE jobs SET status = 'succeeded', updated_at = now() WHERE id = ?::uuid AND status <> 'succeeded'",
                command.jobId
            )
            log.info("Skip double-post postId={} already published mediaId={}", command.postId, postRow.mediaId)
            return
        }

        jdbc.update(
            "UPDATE jobs SET status = 'running', attempts = attempts + 1, updated_at = now() WHERE id = ?::uuid",
            command.jobId
        )
        jdbc.update(
            "UPDATE posts SET status = 'publishing', updated_at = now() WHERE id = ?::uuid",
            command.postId
        )

        try {
            planGate.assertPublishAllowed(command.tenantId)

            val strategy = strategies.get(command.target)
            val quota = strategy.quota(command)
            val decision = quotaGovernor.decide(quota, command.correlationId)
            if (!decision.allow) {
                deferForQuota(command, json, quota.usage, quota.total)
                return
            }
            if (decision.nearLimit) {
                notifyNearQuota(command, quota.usage, quota.total)
            }

            val result = strategy.publish(command)

            jdbc.update(
                """
                UPDATE posts SET status = 'published',
                  ig_container_id = COALESCE(ig_container_id, ?),
                  ig_media_id = ?, ig_permalink = ?,
                  published_at = now(), updated_at = now()
                WHERE id = ?::uuid
                """.trimIndent(),
                result.containerId,
                result.mediaId,
                result.permalink,
                command.postId
            )
            jdbc.update(
                "INSERT INTO publish_quota_ledger (tenant_id, ig_user_id) VALUES (?::uuid, ?)",
                command.tenantId,
                command.igUserId
            )

            val body = "posted ${result.permalink}"
            jdbc.update(
                """
                INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, ?)
                """.trimIndent(),
                command.tenantId,
                command.clientId,
                command.postId,
                body,
                "packages/meta-mocks/fixtures/whatsapp/outbound-posted.json"
            )

            val eventPayload = mapper.createObjectNode()
                .put("igPermalink", result.permalink)
                .put("igMediaId", result.mediaId)
                .put("target", result.target.name)
                .put("outbound", body)
                .toString()

            jdbc.update(
                "UPDATE jobs SET status = 'succeeded', result = ?::jsonb, updated_at = now() WHERE id = ?::uuid",
                eventPayload,
                command.jobId
            )

            // Published exactly once per post (guarded by unique-ish insert on first success path).
            val alreadyEmitted = jdbc.query(
                """
                SELECT 1 FROM domain_events
                WHERE event_type = 'Published' AND aggregate_id = ?::uuid LIMIT 1
                """.trimIndent(),
                ResultSetExtractor { rs -> rs.next() },
                command.postId
            ) == true
            if (!alreadyEmitted) {
                jdbc.update(
                    """
                    INSERT INTO domain_events (tenant_id, event_type, aggregate_type, aggregate_id, correlation_id, payload)
                    VALUES (?::uuid, 'Published', 'post', ?::uuid, ?, ?::jsonb)
                    """.trimIndent(),
                    command.tenantId,
                    command.postId,
                    command.correlationId,
                    eventPayload
                )
                @Suppress("UNCHECKED_CAST")
                val stream = redis.opsForStream<String, String>()
                stream.add(
                    "domain-events",
                    mapOf(
                        "type" to "Published",
                        "aggregateId" to command.postId,
                        "correlationId" to (command.correlationId ?: ""),
                        "payload" to eventPayload
                    )
                )
            }

            log.info(
                "Published postId={} permalink={} target={} correlationId={}",
                command.postId,
                result.permalink,
                result.target,
                command.correlationId
            )
        } catch (ex: TenantPlanGate.PlanBlockedException) {
            failJob(command, "plan_blocked:${ex.message}", "publish_failed")
        } catch (ex: MetaTokenVault.NeedsReauthException) {
            tokens.markNeedsReauth(command.tenantId)
            emitNeedsReauth(command, ex.message ?: "needs_reauth")
            failJob(command, "needs_reauth:${ex.message}", "publish_failed")
        } catch (ex: Exception) {
            log.error("Publish failed postId={} err={}", command.postId, ex.message, ex)
            failJob(command, ex.message ?: "publish_failed", "publish_failed")
        }
    }

    private fun deferForQuota(command: PublishCommand, json: String, used: Long, total: Long) {
        jdbc.update(
            """
            UPDATE posts SET status = 'publish_queued',
              scheduled_for = now() + interval '1 hour', updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            command.postId
        )
        jdbc.update(
            "UPDATE jobs SET status = 'queued', last_error = ?, updated_at = now() WHERE id = ?::uuid",
            "quota_exhausted:$used/$total",
            command.jobId
        )
        jdbc.update(
            """
            INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
            VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, NULL)
            """.trimIndent(),
            command.tenantId,
            command.clientId,
            command.postId,
            "Instagram daily publish limit reached ($used/$total). We scheduled your post for the next window."
        )
        streams.enqueueDelayed(json)
        log.warn(
            "Quota exhausted igUserId={} {}/{} postId={} — scheduled on stream.ig-publish-delayed",
            command.igUserId,
            used,
            total,
            command.postId
        )
    }

    private fun notifyNearQuota(command: PublishCommand, used: Long, total: Long) {
        jdbc.update(
            """
            INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
            VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, NULL)
            """.trimIndent(),
            command.tenantId,
            command.clientId,
            command.postId,
            "Approaching Instagram publish limit ($used/$total in rolling window)."
        )
    }

    private fun emitNeedsReauth(command: PublishCommand, reason: String) {
        val payload = mapper.createObjectNode().put("reason", reason).put("tenantId", command.tenantId).toString()
        jdbc.update(
            """
            INSERT INTO domain_events (tenant_id, event_type, aggregate_type, aggregate_id, correlation_id, payload)
            VALUES (?::uuid, 'NeedsReauth', 'tenant', ?::uuid, ?, ?::jsonb)
            """.trimIndent(),
            command.tenantId,
            command.tenantId,
            command.correlationId,
            payload
        )
        jdbc.update(
            """
            INSERT INTO outbound_messages (tenant_id, client_id, post_id, channel, body, fixture_path)
            VALUES (?::uuid, ?::uuid, ?::uuid, 'whatsapp', ?, NULL)
            """.trimIndent(),
            command.tenantId,
            command.clientId,
            command.postId,
            "Reconnect Instagram — publishing paused until you re-authorize."
        )
    }

    private fun failJob(command: PublishCommand, error: String, postStatus: String) {
        jdbc.update(
            "UPDATE jobs SET status = 'failed', last_error = ?, updated_at = now() WHERE id = ?::uuid",
            error,
            command.jobId
        )
        jdbc.update(
            "UPDATE posts SET status = ?, updated_at = now() WHERE id = ?::uuid",
            postStatus,
            command.postId
        )
    }

    private fun toCommand(root: JsonNode): PublishCommand {
        val payload = root.path("payload")
        val targetRaw = payload.path("target").asText("INSTAGRAM")
        val target = runCatching { PublishTargetType.valueOf(targetRaw) }
            .getOrDefault(PublishTargetType.INSTAGRAM)
        return PublishCommand(
            jobId = root.path("jobId").asText(),
            tenantId = root.path("tenantId").asText(),
            clientId = root.path("clientId").asText(null),
            postId = payload.path("postId").asText(),
            igUserId = payload.path("igUserId").asText("ig-unknown"),
            caption = payload.path("caption").asText(""),
            imageUrl = payload.path("imageUrl").asText(""),
            metaMode = payload.path("metaMode").asText("mock"),
            fixturePermalink = payload.path("fixturePermalink").asText(null),
            // Soft product hint only — live/fixture quota_total is authoritative in strategy.quota()
            quotaTotal = payload.path("quotaTotal").asInt(0),
            target = target,
            correlationId = payload.path("correlationId").asText(null)
        )
    }
}

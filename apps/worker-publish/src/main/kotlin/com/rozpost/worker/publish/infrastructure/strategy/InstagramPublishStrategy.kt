package com.rozpost.worker.publish.infrastructure.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.rozpost.worker.publish.domain.PublishCommand
import com.rozpost.worker.publish.domain.PublishResult
import com.rozpost.worker.publish.domain.PublishTargetType
import com.rozpost.worker.publish.domain.QuotaSnapshot
import com.rozpost.worker.publish.infrastructure.meta.GraphHttpClient
import com.rozpost.worker.publish.infrastructure.meta.MetaTokenVault
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Component
class InstagramPublishStrategy(
    private val jdbc: JdbcTemplate,
    private val tokens: MetaTokenVault,
    private val graph: GraphHttpClient,
    private val mapper: ObjectMapper,
    @Value("\${rozpost.meta-mode:mock}") private val defaultMode: String,
    @Value("\${rozpost.fixture-permalink:https://www.instagram.com/p/DEMO_ROZPOST_POSTED/}")
    private val defaultPermalink: String,
    @Value("\${META_GRAPH_VERSION:v21.0}") private val graphVersion: String,
    @Value("\${rozpost.ig-container-poll-ms:1500}") private val pollMs: Long,
    @Value("\${rozpost.ig-container-poll-max:40}") private val pollMax: Int
) : PublishTargetStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override val target: PublishTargetType = PublishTargetType.INSTAGRAM

    /**
     * Always consult content_publishing_limit (live Graph or META_MODE=mock fixture).
     * Never treat a hardcoded 25 as source of truth — use live/fixture config.quota_total.
     */
    override fun quota(command: PublishCommand): QuotaSnapshot {
        val ledgerUsed = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM publish_quota_ledger
            WHERE ig_user_id = ? AND published_at >= now() - interval '24 hours'
            """.trimIndent(),
            Long::class.java,
            command.igUserId
        ) ?: 0L

        val (apiUsage, apiTotal) = fetchPublishingLimit(command)
        // Trust live totals; ledger is local safety net for mock/partial failures.
        return QuotaSnapshot(usage = maxOf(ledgerUsed, apiUsage), total = apiTotal)
    }

    override fun publish(command: PublishCommand): PublishResult {
        val mock = isMock(command)
        if (mock) {
            val permalink = command.fixturePermalink?.takeIf { it.isNotBlank() } ?: defaultPermalink
            log.info(
                "IG mock publish postId={} igUserId={} correlationId={}",
                command.postId,
                command.igUserId,
                command.correlationId
            )
            return PublishResult(
                containerId = "mock_container_${command.postId.take(8)}",
                mediaId = "mock_media_${command.postId.take(8)}",
                permalink = permalink,
                target = PublishTargetType.INSTAGRAM
            )
        }

        val accessToken = tokens.accessTokenForTenant(command.tenantId)
        val existingContainer = jdbc.query(
            "SELECT ig_container_id FROM posts WHERE id = ?::uuid",
            ResultSetExtractor { rs -> if (rs.next()) rs.getString(1) else null },
            command.postId
        )
        val containerId = if (!existingContainer.isNullOrBlank()) {
            log.info("Reusing ig_container_id={} for postId={} (no recreate)", existingContainer, command.postId)
            waitUntilFinished(existingContainer, accessToken)
            existingContainer
        } else {
            val created = createContainer(command, accessToken)
            jdbc.update(
                "UPDATE posts SET ig_container_id = ?, updated_at = now() WHERE id = ?::uuid",
                created,
                command.postId
            )
            waitUntilFinished(created, accessToken)
            created
        }

        // Idempotency: if ig_media_id already set, do not call media_publish again.
        data class MediaSnap(val mediaId: String?, val permalink: String?)
        val existingMedia = jdbc.query(
            "SELECT ig_media_id, ig_permalink FROM posts WHERE id = ?::uuid",
            ResultSetExtractor { rs ->
                if (rs.next()) MediaSnap(rs.getString("ig_media_id"), rs.getString("ig_permalink")) else null
            },
            command.postId
        )
        if (existingMedia != null && !existingMedia.mediaId.isNullOrBlank()) {
            return PublishResult(
                containerId = containerId,
                mediaId = existingMedia.mediaId,
                permalink = existingMedia.permalink ?: defaultPermalink,
                target = PublishTargetType.INSTAGRAM
            )
        }

        val mediaId = mediaPublish(command.igUserId, containerId, accessToken)
        val permalink = fetchPermalink(mediaId, accessToken) ?: defaultPermalink
        return PublishResult(containerId, mediaId, permalink, PublishTargetType.INSTAGRAM)
    }

    private fun fetchPublishingLimit(command: PublishCommand): Pair<Long, Long> {
        if (isMock(command)) {
            return loadMockQuotaFixture()
        }
        val token = tokens.accessTokenForTenant(command.tenantId)
        val url =
            "https://graph.facebook.com/$graphVersion/${command.igUserId}/content_publishing_limit" +
                "?fields=quota_usage,config&access_token=$token"
        val body = graph.get(url)
        val usage = Regex("\"quota_usage\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLong() ?: 0L
        val total = Regex("\"quota_total\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLong()
            ?: error("content_publishing_limit missing config.quota_total — refusing hardcoded fallback")
        return usage to total
    }

    private fun loadMockQuotaFixture(): Pair<Long, Long> {
        // Prefer packages fixture when running from monorepo cwd; else classpath / defaults from fixture file content.
        val candidates = listOf(
            Path.of("packages/meta-mocks/fixtures/instagram/content-publishing-limit.json"),
            Path.of("../packages/meta-mocks/fixtures/instagram/content-publishing-limit.json"),
            Path.of("../../packages/meta-mocks/fixtures/instagram/content-publishing-limit.json")
        )
        val json = candidates.firstOrNull { Files.isRegularFile(it) }?.let { Files.readString(it) }
            ?: runCatching {
                ClassPathResource("fixtures/instagram/content-publishing-limit.json").inputStream.bufferedReader().readText()
            }.getOrNull()
            ?: """{"response":{"data":[{"quota_usage":3,"config":{"quota_total":25,"quota_duration":86400}}]}}"""

        val node = mapper.readTree(json)
        val data = node.path("response").path("data").path(0).takeIf { !it.isMissingNode }
            ?: node.path("data").path(0)
        val usage = data.path("quota_usage").asLong(0)
        val total = data.path("config").path("quota_total").asLong(-1)
        require(total > 0) { "mock fixture missing config.quota_total" }
        return usage to total
    }

    private fun createContainer(command: PublishCommand, token: String): String {
        val url = "https://graph.facebook.com/$graphVersion/${command.igUserId}/media" +
            "?image_url=${enc(command.imageUrl)}&caption=${enc(command.caption)}&access_token=$token"
        val body = graph.post(url)
        return extractId(body)
    }

    private fun waitUntilFinished(containerId: String, token: String) {
        repeat(pollMax) { attempt ->
            val url =
                "https://graph.facebook.com/$graphVersion/$containerId?fields=status_code&access_token=$token"
            val body = graph.get(url)
            val status = Regex("\"status_code\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: Regex("\"status_code\"\\s*:\\s*([A-Z_]+)").find(body)?.groupValues?.get(1)
            when (status) {
                "FINISHED" -> return
                "ERROR" -> error("IG container $containerId status ERROR: ${body.take(300)}")
                else -> {
                    // IN_PROGRESS / 2207027 style — poll, do not recreate
                    log.info("IG container {} status={} attempt={}", containerId, status, attempt + 1)
                    Thread.sleep(pollMs)
                }
            }
        }
        error("IG container $containerId did not reach FINISHED within poll budget")
    }

    private fun mediaPublish(igUserId: String, containerId: String, token: String): String {
        val url = "https://graph.facebook.com/$graphVersion/$igUserId/media_publish" +
            "?creation_id=$containerId&access_token=$token"
        // Intentionally no blind auto-retry layer here — Resilience4j retries only clear transport failures;
        // media_publish must remain create-once with ig_media_id checkpoint above.
        return extractId(graph.post(url))
    }

    private fun fetchPermalink(mediaId: String, token: String): String? {
        val url =
            "https://graph.facebook.com/$graphVersion/$mediaId?fields=permalink&access_token=$token"
        val body = graph.get(url)
        return Regex("\"permalink\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }

    private fun isMock(command: PublishCommand) =
        command.metaMode != "live" || defaultMode != "live"

    private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)

    private fun extractId(json: String): String {
        val i = json.indexOf("\"id\"")
        require(i >= 0) { "no id in response: $json" }
        val colon = json.indexOf(':', i)
        val q1 = json.indexOf('"', colon + 1)
        val q2 = json.indexOf('"', q1 + 1)
        return json.substring(q1 + 1, q2)
    }
}

@Component
class UnsupportedFacebookPublishStrategy : PublishTargetStrategy {
    override val target = PublishTargetType.FACEBOOK
    override fun quota(command: PublishCommand) =
        throw UnsupportedOperationException("Facebook publish not enabled")
    override fun publish(command: PublishCommand) =
        throw UnsupportedOperationException("Facebook publish not enabled")
}

@Component
class UnsupportedTikTokPublishStrategy : PublishTargetStrategy {
    override val target = PublishTargetType.TIKTOK
    override fun quota(command: PublishCommand) =
        throw UnsupportedOperationException("TikTok publish not enabled")
    override fun publish(command: PublishCommand) =
        throw UnsupportedOperationException("TikTok publish not enabled")
}

@Component
class PublishStrategyRegistry(strategies: List<PublishTargetStrategy>) {
    private val byTarget = strategies.associateBy { it.target }

    fun get(target: PublishTargetType): PublishTargetStrategy =
        byTarget[target] ?: throw IllegalArgumentException("No publish strategy for $target")
}

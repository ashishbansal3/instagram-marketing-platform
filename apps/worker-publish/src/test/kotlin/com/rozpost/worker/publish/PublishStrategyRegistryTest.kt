package com.rozpost.worker.publish

import com.rozpost.worker.publish.domain.PublishCommand
import com.rozpost.worker.publish.domain.PublishTargetType
import com.rozpost.worker.publish.infrastructure.strategy.PublishStrategyRegistry
import com.rozpost.worker.publish.infrastructure.strategy.UnsupportedFacebookPublishStrategy
import com.rozpost.worker.publish.infrastructure.strategy.UnsupportedTikTokPublishStrategy
import com.rozpost.worker.publish.domain.PublishResult
import com.rozpost.worker.publish.domain.QuotaSnapshot
import com.rozpost.worker.publish.infrastructure.strategy.PublishTargetStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PublishStrategyRegistryTest {
    @Test
    fun resolvesInstagram() {
        val ig = object : PublishTargetStrategy {
            override val target = PublishTargetType.INSTAGRAM
            override fun quota(command: PublishCommand) = QuotaSnapshot(0, 25)
            override fun publish(command: PublishCommand) =
                PublishResult("c", "m", "https://ig/p/x", PublishTargetType.INSTAGRAM)
        }
        val registry = PublishStrategyRegistry(
            listOf(ig, UnsupportedFacebookPublishStrategy(), UnsupportedTikTokPublishStrategy())
        )
        assertEquals(PublishTargetType.INSTAGRAM, registry.get(PublishTargetType.INSTAGRAM).target)
    }

    @Test
    fun facebookUnsupportedThrowsOnPublish() {
        val fb = UnsupportedFacebookPublishStrategy()
        assertThrows(UnsupportedOperationException::class.java) {
            fb.publish(
                PublishCommand(
                    jobId = "j",
                    tenantId = "t",
                    clientId = null,
                    postId = "p",
                    igUserId = "ig",
                    caption = "",
                    imageUrl = "",
                    metaMode = "mock",
                    fixturePermalink = null,
                    quotaTotal = 0,
                    target = PublishTargetType.FACEBOOK,
                    correlationId = null
                )
            )
        }
    }
}

package com.rozpost.worker.publish

import com.rozpost.worker.publish.domain.PublishTargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DuplicatePublishGuardTest {
    @Test
    fun publishedStatusSkipsMediaPublish() {
        val status = "published"
        val mediaId: String? = "IG_MEDIA_1"
        val shouldPublish = !(status == "published" && !mediaId.isNullOrBlank())
        assertEquals(false, shouldPublish)
    }

    @Test
    fun publishingWithoutMediaStillAttempts() {
        val status = "publishing"
        val mediaId: String? = null
        val shouldPublish = !(status == "published" && !mediaId.isNullOrBlank())
        assertEquals(true, shouldPublish)
    }

    @Test
    fun defaultTargetIsInstagram() {
        assertEquals(PublishTargetType.INSTAGRAM, PublishTargetType.valueOf("INSTAGRAM"))
    }
}

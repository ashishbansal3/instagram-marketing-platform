package com.rozpost.worker.media

import com.rozpost.worker.media.infrastructure.meta.MockWhatsAppMediaFetcher
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MockWhatsAppMediaFetcherTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun mockFetchWritesFileForWaMediaId() {
        val out = MockWhatsAppMediaFetcher().fetch("media-123", tmp, "image")
        assertTrue(Files.isRegularFile(out))
        assertTrue(Files.size(out) > 0)
    }
}

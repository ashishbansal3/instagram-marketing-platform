package com.rozpost.worker.media.domain.port

import java.nio.file.Path

/** Port: fetch WhatsApp Cloud API media bytes (live Graph or META_MODE=mock fixture). */
interface WhatsAppMediaFetcher {
    /**
     * Resolve WA media id to local bytes under [destDir].
     * Mock mode uses bundled fixture; live calls Graph media URL then downloads.
     */
    fun fetchToFile(waMediaId: String, destDir: Path, kind: String = "image"): Path
}

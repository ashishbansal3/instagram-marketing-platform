package com.rozpost.worker.media.domain.strategy

import com.rozpost.worker.media.domain.model.CaptionResult
import com.rozpost.worker.media.domain.model.EnhanceResult
import java.nio.file.Path

/**
 * Open/Closed: add providers by implementing these strategies and registering them —
 * do not edit existing provider classes to introduce a new vendor (e.g. Kling later).
 */
interface EnhanceStrategy {
    val name: String
    val priority: Int
    fun available(): Boolean
    fun enhance(
        input: Path,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): EnhanceResult
}

interface CutStrategy {
    val name: String
    fun reframeTo916(input: Path, output: Path, durationSeconds: Int, correlationId: String?): Path
}

interface CaptionStrategy {
    val name: String
    val priority: Int
    fun available(): Boolean
    fun assemble(
        videoOrImage: Path,
        caption: String,
        locale: String,
        instructionText: String?,
        brandName: String?,
        workDir: Path,
        correlationId: String?,
    ): CaptionResult
}

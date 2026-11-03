package com.rozpost.worker.media.infrastructure.cut

import com.rozpost.worker.media.domain.error.MediaProcessingException
import com.rozpost.worker.media.domain.strategy.CutStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Component
class FfmpegCutStrategy : CutStrategy {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "ffmpeg"

    override fun reframeTo916(
        input: Path,
        output: Path,
        durationSeconds: Int,
        correlationId: String?,
    ): Path {
        Files.createDirectories(output.parent)
        val isVideo = input.fileName.toString().lowercase().let {
            it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".webm") || it.endsWith(".mkv")
        }
        val vf = "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920"
        val cmd = if (isVideo) {
            listOf(
                "ffmpeg", "-y", "-i", input.toAbsolutePath().toString(),
                "-vf", vf, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an",
                "-t", durationSeconds.toString(),
                output.toAbsolutePath().toString(),
            )
        } else {
            listOf(
                "ffmpeg", "-y", "-loop", "1", "-i", input.toAbsolutePath().toString(),
                "-vf", "$vf,zoompan=z='min(zoom+0.0008,1.08)':d=125:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=1080x1920:fps=25",
                "-t", durationSeconds.toString(),
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an",
                output.toAbsolutePath().toString(),
            )
        }
        log.info("ffmpeg cut correlationId={} cmd={}", correlationId, cmd.joinToString(" "))
        run(cmd)
        if (!Files.exists(output) || Files.size(output) == 0L) {
            throw MediaProcessingException("ffmpeg produced empty output $output")
        }
        return output
    }

    private fun run(cmd: List<String>) {
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        // Drain stdout asynchronously — readText()-then-waitFor deadlocks the timeout.
        val outFuture = java.util.concurrent.Executors.newSingleThreadExecutor().submit<String> {
            proc.inputStream.bufferedReader().readText()
        }
        val ok = proc.waitFor(120, TimeUnit.SECONDS)
        if (!ok) {
            proc.destroyForcibly()
            outFuture.cancel(true)
            throw MediaProcessingException("ffmpeg timed out")
        }
        val out = try {
            outFuture.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            ""
        }
        if (proc.exitValue() != 0) {
            throw MediaProcessingException("ffmpeg exit ${proc.exitValue()}: ${out.takeLast(1500)}")
        }
    }
}

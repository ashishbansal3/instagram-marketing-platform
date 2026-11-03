package com.rozpost.worker.media.infrastructure.storage

import com.rozpost.worker.media.infrastructure.config.MediaProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class ObjectStorageService(
    private val s3: S3Client,
    private val props: MediaProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var storageReady = false

    @PostConstruct
    fun ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.s3.bucket).build())
            storageReady = true
        } catch (_: NoSuchBucketException) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(props.s3.bucket).build())
                storageReady = true
                log.info("Created bucket {}", props.s3.bucket)
            } catch (e: Exception) {
                log.warn("S3 bucket create failed — local filesystem fallback: {}", e.message)
                storageReady = false
            }
        } catch (e: Exception) {
            log.warn("S3 unavailable at {} — local filesystem fallback: {}", props.s3.endpoint, e.message)
            storageReady = false
        }
        Files.createDirectories(localRoot())
    }

    fun putFile(key: String, file: Path, contentType: String): String {
        if (storageReady) {
            try {
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(props.s3.bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                    RequestBody.fromFile(file),
                )
                return publicUrl(key)
            } catch (e: Exception) {
                log.warn("S3 put failed for {}, local fallback: {}", key, e.message)
            }
        }
        val dest = localRoot().resolve(key)
        Files.createDirectories(dest.parent)
        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
        return publicUrl(key)
    }

    fun downloadTo(key: String, dest: Path): Path {
        Files.createDirectories(dest.parent)
        val tmp = dest.resolveSibling("${dest.fileName}.partial-${ProcessHandle.current().pid()}-${Thread.currentThread().threadId()}")
        try {
            if (storageReady) {
                try {
                    s3.getObject(GetObjectRequest.builder().bucket(props.s3.bucket).key(key).build(), tmp)
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    return dest
                } catch (e: Exception) {
                    log.warn("S3 get failed for {}, trying local: {}", key, e.message)
                    runCatching { Files.deleteIfExists(tmp) }
                }
            }
            Files.copy(localRoot().resolve(key), dest, StandardCopyOption.REPLACE_EXISTING)
            return dest
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }

    /** Checkpoint: object must exist in S3 or local fallback before enhance/cut. */
    fun exists(key: String): Boolean {
        if (storageReady) {
            try {
                s3.headObject(
                    software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                        .bucket(props.s3.bucket)
                        .key(key)
                        .build(),
                )
                return true
            } catch (_: Exception) {
                // fall through to local
            }
        }
        return Files.isRegularFile(localRoot().resolve(key))
    }

    fun publicUrl(key: String): String {
        if (!storageReady) {
            return "http://localhost:${System.getenv("WORKER_PORT") ?: "8081"}/api/v1/media/objects/$key"
        }
        return "${props.s3.publicBaseUrl.trimEnd('/')}/$key"
    }

    fun localPathFor(key: String): Path = localRoot().resolve(key)
    fun isReady(): Boolean = storageReady
    private fun localRoot(): Path = Path.of(props.workDir, "objects")
}

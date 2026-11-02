package com.rozpost.worker.publish.infrastructure.meta

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Resilience4j-wrapped Graph HTTP. Respects 429 Retry-After via Retry config + explicit sleep. */
@Component
class GraphHttpClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    class RateLimitedException(val retryAfterSeconds: Long, message: String) : RuntimeException(message)

    @CircuitBreaker(name = "metaGraph")
    @Retry(name = "metaGraph")
    fun get(url: String): String = exchange("GET", url, null)

    @CircuitBreaker(name = "metaGraph")
    @Retry(name = "metaGraph")
    fun post(url: String): String = exchange("POST", url, HttpRequest.BodyPublishers.noBody())

    private fun exchange(method: String, url: String, body: HttpRequest.BodyPublisher?): String {
        // Never log URL with access_token query param in full — redact.
        log.debug("graph {} {}", method, redact(url))
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        val req = if (method == "POST") {
            builder.POST(body ?: HttpRequest.BodyPublishers.noBody()).build()
        } else {
            builder.GET().build()
        }
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 429) {
            val retryAfter = resp.headers().firstValue("Retry-After").orElse("60").toLongOrNull() ?: 60L
            throw RateLimitedException(retryAfter, "Graph 429 retry-after=${retryAfter}s")
        }
        require(resp.statusCode() < 300) { "Graph HTTP ${resp.statusCode()}: ${resp.body().take(400)}" }
        return resp.body()
    }

    private fun redact(url: String): String =
        url.replace(Regex("access_token=[^&]+"), "access_token=REDACTED")
}

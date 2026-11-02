package com.rozpost.worker.media.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.web.client.RestClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@Configuration
class AppConfig(private val props: MediaProperties) {

    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()

    @Bean
    fun s3Client(): S3Client {
        Files.createDirectories(Path.of(props.workDir))
        val credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.s3.accessKey, props.s3.secretKey),
        )
        return S3Client.builder()
            .endpointOverride(URI.create(props.s3.endpoint))
            .region(Region.of(props.s3.region.ifBlank { "us-east-1" }))
            .credentialsProvider(credentials)
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(props.s3.pathStyle)
                    .build(),
            )
            .build()
    }

    @Bean
    @ConditionalOnProperty(name = ["shedlock.enabled"], havingValue = "true", matchIfMissing = true)
    fun lockProvider(connectionFactory: RedisConnectionFactory): LockProvider =
        RedisLockProvider(connectionFactory, "worker-media")
}

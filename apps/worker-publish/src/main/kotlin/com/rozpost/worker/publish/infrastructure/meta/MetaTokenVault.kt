package com.rozpost.worker.publish.infrastructure.meta

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts Meta tokens in-process only. Ciphertext stays in Postgres; never Redis/logs.
 * AES-256-GCM envelope: iv(12) || ciphertext+tag. Key from TOKEN_ENCRYPTION_KEY (base64 32 bytes)
 * or META_MODE=mock plaintext placeholder bytes.
 */
@Component
class MetaTokenVault(
    private val jdbc: JdbcTemplate,
    @Value("\${TOKEN_ENCRYPTION_KEY:}") private val keyB64: String,
    @Value("\${rozpost.meta-mode:mock}") private val metaMode: String
) {
    class NeedsReauthException(message: String) : RuntimeException(message)

    fun accessTokenForTenant(tenantId: String, provider: String = "instagram_login"): String {
        val row = jdbc.query(
            """
            SELECT access_token_enc, status FROM meta_connections
            WHERE tenant_id = ?::uuid AND provider = ? LIMIT 1
            """.trimIndent(),
            ResultSetExtractor { rs ->
                if (!rs.next()) null
                else rs.getBytes("access_token_enc") to rs.getString("status")
            },
            tenantId,
            provider
        ) ?: throw NeedsReauthException("no meta_connection for tenant=$tenantId")

        val (enc, status) = row
        if (status == "needs_reauth" || status == "revoked") {
            throw NeedsReauthException("meta_connection status=$status tenant=$tenantId")
        }
        if (enc == null || enc.isEmpty()) {
            throw NeedsReauthException("empty access_token_enc tenant=$tenantId")
        }

        if (metaMode != "live" || keyB64.isBlank()) {
            // Mock / missing key: treat stored bytes as opaque mock token material (not logged).
            return String(enc, Charsets.UTF_8)
        }

        return try {
            decrypt(enc, tenantId, provider)
        } catch (ex: Exception) {
            throw NeedsReauthException("token decrypt failed tenant=$tenantId: ${ex.message}")
        }
    }

    fun markNeedsReauth(tenantId: String, provider: String = "instagram_login") {
        jdbc.update(
            """
            UPDATE meta_connections SET status = 'needs_reauth'
            WHERE tenant_id = ?::uuid AND provider = ?
            """.trimIndent(),
            tenantId,
            provider
        )
    }

    private fun decrypt(blob: ByteArray, tenantId: String, provider: String): String {
        val keyBytes = Base64.getDecoder().decode(keyB64)
        require(keyBytes.size == 32) { "TOKEN_ENCRYPTION_KEY must decode to 32 bytes" }
        require(blob.size > 12) { "ciphertext too short" }
        val iv = blob.copyOfRange(0, 12)
        val cipherBytes = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("$tenantId|$provider".toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }
}

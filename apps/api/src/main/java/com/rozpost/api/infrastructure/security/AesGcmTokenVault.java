package com.rozpost.api.infrastructure.security;

import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM envelope for Meta tokens at rest. Key from TOKEN_ENCRYPTION_KEY (base64 32 bytes). */
@Component
public class AesGcmTokenVault {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private final RozpostProperties props;
    private final SecureRandom random = new SecureRandom();

    public AesGcmTokenVault(RozpostProperties props) {
        this.props = props;
    }

    public String encrypt(String plaintext, String aadTenantAndProvider) {
        byte[] keyBytes = decodeKey();
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aadTenantAndProvider.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("token_encrypt_failed", e);
        }
    }

    public String decrypt(String envelopeB64, String aadTenantAndProvider) {
        byte[] keyBytes = decodeKey();
        try {
            byte[] all = Base64.getDecoder().decode(envelopeB64);
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aadTenantAndProvider.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("token_decrypt_failed", e);
        }
    }

    private byte[] decodeKey() {
        String k = props.getTokenEncryptionKey();
        if (k == null || k.isBlank()) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY not configured");
        }
        byte[] key = Base64.getDecoder().decode(k);
        if (key.length != 32) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be 32 bytes base64");
        }
        return key;
    }
}

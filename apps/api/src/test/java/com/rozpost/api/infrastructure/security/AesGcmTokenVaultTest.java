package com.rozpost.api.infrastructure.security;

import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmTokenVaultTest {

    @Test
    void roundTripWithAad() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        RozpostProperties props = new RozpostProperties();
        props.setTokenEncryptionKey(Base64.getEncoder().encodeToString(key));
        AesGcmTokenVault vault = new AesGcmTokenVault(props);

        String aad = "tenant-1:meta";
        String envelope = vault.encrypt("ig-token-secret", aad);
        assertNotEquals("ig-token-secret", envelope);
        assertEquals("ig-token-secret", vault.decrypt(envelope, aad));
        assertThrows(IllegalStateException.class, () -> vault.decrypt(envelope, "other-aad"));
    }
}

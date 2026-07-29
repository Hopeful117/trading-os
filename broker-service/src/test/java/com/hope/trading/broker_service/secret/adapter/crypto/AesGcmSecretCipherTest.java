package com.hope.trading.broker_service.secret.adapter.crypto;

import com.hope.trading.broker_service.secret.application.SecretDecryptionException;
import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmSecretCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripUsesUniqueIvAndDoesNotExposePlaintext() {
        AesGcmSecretCipher cipher = cipher(KEY, "v1");
        byte[] value = "FAKE_SENTINEL_CREDENTIAL".getBytes(StandardCharsets.UTF_8);
        EncryptedSecret first;
        EncryptedSecret second;
        try (PlainSecret plain = new PlainSecret(value)) {
            first = cipher.encrypt(plain);
            second = cipher.encrypt(plain);
        }
        assertFalse(Arrays.equals(first.ciphertext(), value));
        assertFalse(Arrays.equals(first.initializationVector(), second.initializationVector()));
        assertNotEquals(Base64.getEncoder().encodeToString(first.ciphertext()),
                Base64.getEncoder().encodeToString(second.ciphertext()));
        try (PlainSecret decrypted = cipher.decrypt(first)) {
            assertArrayEquals(value, decrypted.copyValue());
        }
    }

    @Test
    void rejectsTamperingWrongKeyAndWrongVersionWithoutSensitiveMessage() {
        AesGcmSecretCipher cipher = cipher(KEY, "v1");
        EncryptedSecret encrypted;
        try (PlainSecret plain = new PlainSecret("FAKE_SENTINEL_CREDENTIAL".getBytes(StandardCharsets.UTF_8))) {
            encrypted = cipher.encrypt(plain);
        }
        byte[] altered = encrypted.ciphertext();
        altered[0] ^= 1;
        SecretDecryptionException tampered = assertThrows(SecretDecryptionException.class,
                () -> cipher.decrypt(new EncryptedSecret(altered, encrypted.initializationVector(),
                        encrypted.algorithm(), encrypted.keyVersion())));
        assertFalse(tampered.getMessage().contains("FAKE_SENTINEL"));
        assertThrows(SecretDecryptionException.class, () -> cipher("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", "v1")
                .decrypt(encrypted));
        assertThrows(SecretDecryptionException.class, () -> cipher.decrypt(
                new EncryptedSecret(encrypted.ciphertext(), encrypted.initializationVector(),
                        encrypted.algorithm(), "missing")));
    }

    @Test
    void validatesMasterKeyConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> new EnvironmentKeyProvider(new SecretKeyProperties("", "v1")));
        assertThrows(IllegalStateException.class,
                () -> new EnvironmentKeyProvider(new SecretKeyProperties("ZmFrZQ==", "v1")));
    }

    private AesGcmSecretCipher cipher(String key, String version) {
        return new AesGcmSecretCipher(new EnvironmentKeyProvider(new SecretKeyProperties(key, version)),
                new SecureRandom());
    }
}

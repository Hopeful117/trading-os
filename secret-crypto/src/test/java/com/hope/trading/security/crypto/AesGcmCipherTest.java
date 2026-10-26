package com.hope.trading.security.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmCipherTest {
    private static final byte[] KEY = new byte[32];

    @Test
    void encryptsAndDecryptsWithoutReusingIv() {
        AesGcmCipher cipher = new AesGcmCipher(provider(), new SecureRandom());
        byte[] value = "sentinel".getBytes(StandardCharsets.UTF_8);

        EncryptedPayload first = cipher.encrypt(value);
        EncryptedPayload second = cipher.encrypt(value);

        assertArrayEquals(value, cipher.decrypt(first));
        assertNotEquals(java.util.Arrays.toString(first.initializationVector()),
                java.util.Arrays.toString(second.initializationVector()));
    }

    @Test
    void rejectsTamperedPayloadAndUnknownKeyVersion() {
        AesGcmCipher cipher = new AesGcmCipher(provider(), new SecureRandom());
        EncryptedPayload encrypted = cipher.encrypt("sentinel".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = encrypted.ciphertext();
        tampered[0] ^= 1;

        assertThrows(CryptoException.class, () -> cipher.decrypt(new EncryptedPayload(
                tampered, encrypted.initializationVector(), encrypted.algorithm(), encrypted.keyVersion())));
        assertThrows(CryptoException.class, () -> cipher.decrypt(new EncryptedPayload(
                encrypted.ciphertext(), encrypted.initializationVector(), encrypted.algorithm(), "missing")));
    }

    private KeyProvider provider() {
        SecretKey key = new SecretKeySpec(KEY, "AES");
        return new KeyProvider() {
            @Override public String activeVersion() { return "v1"; }
            @Override public SecretKey activeKey() { return key; }
            @Override public SecretKey key(String version) {
                if (!"v1".equals(version)) throw new IllegalStateException("unknown key");
                return key;
            }
        };
    }
}

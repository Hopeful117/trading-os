package com.hope.trading.market_data.ctrader.authentication;

import com.hope.trading.security.crypto.AesGcmCipher;
import com.hope.trading.security.crypto.KeyProvider;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class CTraderTokenCryptoAdapterTest {
    @Test
    void encryptsBothTokenValuesWithoutRetainingPlaintextInPayload() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        KeyProvider provider = new KeyProvider() {
            public String activeVersion() { return "v1"; }
            public SecretKey activeKey() { return key; }
            public SecretKey key(String version) { return key; }
        };
        CTraderTokenCryptoAdapter adapter = new CTraderTokenCryptoAdapter(
                new AesGcmCipher(provider, new SecureRandom()));

        CTraderTokenCryptoAdapter.EncryptedToken encrypted = adapter.encrypt("access-token".toCharArray());

        assertFalse(encrypted.ciphertext().contains("access-token"));
        assertArrayEquals("access-token".toCharArray(), adapter.decrypt(encrypted));
    }
}

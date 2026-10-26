package com.hope.trading.market_data.ctrader.authentication;

import com.hope.trading.security.crypto.AesGcmCipher;
import com.hope.trading.security.crypto.EncryptedPayload;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderTokenCryptoAdapter {
    private final AesGcmCipher cipher;

    public CTraderTokenCryptoAdapter(AesGcmCipher cipher) { this.cipher = cipher; }

    public EncryptedToken encrypt(char[] token) {
        byte[] plain = new String(token).getBytes(StandardCharsets.UTF_8);
        try {
            EncryptedPayload payload = cipher.encrypt(plain);
            return new EncryptedToken(Base64.getEncoder().encodeToString(payload.ciphertext()),
                    Base64.getEncoder().encodeToString(payload.initializationVector()),
                    payload.algorithm(), payload.keyVersion());
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public char[] decrypt(EncryptedToken token) {
        byte[] plain = cipher.decrypt(new EncryptedPayload(
                Base64.getDecoder().decode(token.ciphertext()), Base64.getDecoder().decode(token.initializationVector()),
                token.algorithm(), token.keyVersion()));
        try {
            return new String(plain, StandardCharsets.UTF_8).toCharArray();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public record EncryptedToken(String ciphertext, String initializationVector, String algorithm, String keyVersion) {}
}

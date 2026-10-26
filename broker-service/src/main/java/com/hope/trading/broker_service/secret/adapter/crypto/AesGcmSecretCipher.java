package com.hope.trading.broker_service.secret.adapter.crypto;

import com.hope.trading.broker_service.secret.application.KeyProvider;
import com.hope.trading.broker_service.secret.application.SecretCipher;
import com.hope.trading.broker_service.secret.application.SecretDecryptionException;
import com.hope.trading.broker_service.secret.application.SecretEncryptionException;
import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;

import java.security.SecureRandom;

public class AesGcmSecretCipher implements SecretCipher {
    public static final String ALGORITHM = com.hope.trading.security.crypto.AesGcmCipher.ALGORITHM;

    private final com.hope.trading.security.crypto.AesGcmCipher delegate;

    public AesGcmSecretCipher(KeyProvider keyProvider, SecureRandom secureRandom) {
        this.delegate = new com.hope.trading.security.crypto.AesGcmCipher(
                new com.hope.trading.security.crypto.KeyProvider() {
                    @Override public String activeVersion() { return keyProvider.activeVersion(); }
                    @Override public javax.crypto.SecretKey activeKey() { return keyProvider.activeKey(); }
                    @Override public javax.crypto.SecretKey key(String version) { return keyProvider.key(version); }
                }, secureRandom);
    }

    @Override
    public EncryptedSecret encrypt(PlainSecret secret) {
        byte[] plain = secret.copyValue();
        try {
            var encrypted = delegate.encrypt(plain);
            return new EncryptedSecret(encrypted.ciphertext(), encrypted.initializationVector(),
                    encrypted.algorithm(), encrypted.keyVersion());
        } catch (com.hope.trading.security.crypto.CryptoException exception) {
            throw new SecretEncryptionException();
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    @Override
    public PlainSecret decrypt(EncryptedSecret encrypted) {
        try {
            return new PlainSecret(delegate.decrypt(new com.hope.trading.security.crypto.EncryptedPayload(
                    encrypted.ciphertext(), encrypted.initializationVector(), encrypted.algorithm(),
                    encrypted.keyVersion())));
        } catch (com.hope.trading.security.crypto.CryptoException exception) {
            throw new SecretDecryptionException();
        }
    }
}

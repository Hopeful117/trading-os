package com.hope.trading.broker_service.secret.adapter.crypto;

import com.hope.trading.broker_service.secret.application.KeyProvider;
import com.hope.trading.broker_service.secret.application.SecretCipher;
import com.hope.trading.broker_service.secret.application.SecretDecryptionException;
import com.hope.trading.broker_service.secret.application.SecretEncryptionException;
import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class AesGcmSecretCipher implements SecretCipher {
    public static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom;

    public AesGcmSecretCipher(KeyProvider keyProvider, SecureRandom secureRandom) {
        this.keyProvider = keyProvider;
        this.secureRandom = secureRandom;
    }

    @Override
    public EncryptedSecret encrypt(PlainSecret secret) {
        byte[] plain = secret.copyValue();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.activeKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedSecret(cipher.doFinal(plain), iv, ALGORITHM, keyProvider.activeVersion());
        } catch (GeneralSecurityException exception) {
            throw new SecretEncryptionException();
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    @Override
    public PlainSecret decrypt(EncryptedSecret encrypted) {
        if (!ALGORITHM.equals(encrypted.algorithm())) throw new SecretDecryptionException();
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.key(encrypted.keyVersion()),
                    new GCMParameterSpec(TAG_BITS, encrypted.initializationVector()));
            return new PlainSecret(cipher.doFinal(encrypted.ciphertext()));
        } catch (GeneralSecurityException | IllegalStateException exception) {
            throw new SecretDecryptionException();
        }
    }
}

package com.hope.trading.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AesGcmCipher {
    public static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom;

    public AesGcmCipher(KeyProvider keyProvider, SecureRandom secureRandom) {
        this.keyProvider = keyProvider;
        this.secureRandom = secureRandom;
    }

    public EncryptedPayload encrypt(byte[] plaintext) {
        byte[] copy = Arrays.copyOf(plaintext, plaintext.length);
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.activeKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedPayload(cipher.doFinal(copy), iv, ALGORITHM, keyProvider.activeVersion());
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("Unable to encrypt payload", exception);
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
    }

    public byte[] decrypt(EncryptedPayload encrypted) {
        if (!ALGORITHM.equals(encrypted.algorithm())) throw new CryptoException("Unsupported encryption algorithm");
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.key(encrypted.keyVersion()),
                    new GCMParameterSpec(TAG_BITS, encrypted.initializationVector()));
            return cipher.doFinal(encrypted.ciphertext());
        } catch (GeneralSecurityException | IllegalStateException exception) {
            throw new CryptoException("Unable to decrypt payload", exception);
        }
    }
}

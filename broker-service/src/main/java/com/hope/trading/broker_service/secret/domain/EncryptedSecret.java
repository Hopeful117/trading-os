package com.hope.trading.broker_service.secret.domain;

import java.util.Arrays;

public final class EncryptedSecret {
    private final byte[] ciphertext;
    private final byte[] initializationVector;
    private final String algorithm;
    private final String keyVersion;

    public EncryptedSecret(byte[] ciphertext, byte[] initializationVector, String algorithm, String keyVersion) {
        this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        this.initializationVector = Arrays.copyOf(initializationVector, initializationVector.length);
        this.algorithm = algorithm;
        this.keyVersion = keyVersion;
    }

    public byte[] ciphertext() { return Arrays.copyOf(ciphertext, ciphertext.length); }
    public byte[] initializationVector() { return Arrays.copyOf(initializationVector, initializationVector.length); }
    public String algorithm() { return algorithm; }
    public String keyVersion() { return keyVersion; }

    @Override
    public String toString() {
        return "EncryptedSecret[REDACTED, algorithm=" + algorithm + "]";
    }
}

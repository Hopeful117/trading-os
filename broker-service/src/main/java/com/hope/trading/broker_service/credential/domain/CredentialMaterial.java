package com.hope.trading.broker_service.credential.domain;

import java.util.Arrays;

public final class CredentialMaterial implements AutoCloseable {
    private final char[] apiKey;
    private final char[] apiSecret;
    private final char[] passphrase;

    public CredentialMaterial(char[] apiKey, char[] apiSecret, char[] passphrase) {
        this.apiKey = validatedCopy(apiKey, "apiKey", 8, 256);
        this.apiSecret = validatedCopy(apiSecret, "apiSecret", 16, 512);
        this.passphrase = passphrase == null ? new char[0] : validatedCopy(passphrase, "passphrase", 0, 256);
    }

    private static char[] validatedCopy(char[] value, String name, int min, int max) {
        if (value == null || value.length < min || value.length > max) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
        return Arrays.copyOf(value, value.length);
    }

    public char[] copyApiKey() { return Arrays.copyOf(apiKey, apiKey.length); }
    public char[] copyApiSecret() { return Arrays.copyOf(apiSecret, apiSecret.length); }
    public char[] copyPassphrase() { return Arrays.copyOf(passphrase, passphrase.length); }

    public String apiKeyHint() {
        int visible = Math.min(4, apiKey.length);
        return "••••••••" + new String(apiKey, apiKey.length - visible, visible);
    }

    @Override
    public void close() {
        Arrays.fill(apiKey, '\0');
        Arrays.fill(apiSecret, '\0');
        Arrays.fill(passphrase, '\0');
    }

    @Override
    public String toString() {
        return "CredentialMaterial[REDACTED]";
    }
}

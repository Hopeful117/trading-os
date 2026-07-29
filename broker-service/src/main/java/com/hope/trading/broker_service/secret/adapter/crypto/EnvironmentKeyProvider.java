package com.hope.trading.broker_service.secret.adapter.crypto;

import com.hope.trading.broker_service.secret.application.KeyProvider;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EnvironmentKeyProvider implements KeyProvider {
    private final String version;
    private final SecretKey key;

    public EnvironmentKeyProvider(SecretKeyProperties properties) {
        if (properties.masterKey() == null || properties.masterKey().isBlank()) {
            throw new IllegalStateException("Broker master key is required for stored credential mode");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.masterKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Broker master key must be valid Base64");
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("Broker master key must contain exactly 256 bits");
        }
        if (properties.keyVersion() == null || properties.keyVersion().isBlank()) {
            throw new IllegalStateException("Broker master key version is required");
        }
        version = properties.keyVersion();
        key = new SecretKeySpec(decoded, "AES");
    }

    @Override public String activeVersion() { return version; }
    @Override public SecretKey activeKey() { return key; }
    @Override public SecretKey key(String requestedVersion) {
        if (!version.equals(requestedVersion)) {
            throw new IllegalStateException("Requested broker master key version is unavailable");
        }
        return key;
    }
}

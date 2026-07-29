package com.hope.trading.broker_service.secret.domain;

import java.util.Arrays;

public final class NewSecret implements AutoCloseable {
    private final byte[] value;

    public NewSecret(byte[] value) {
        if (value == null || value.length == 0) throw new IllegalArgumentException("Secret value is required");
        this.value = Arrays.copyOf(value, value.length);
    }

    public byte[] copyValue() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public void close() {
        Arrays.fill(value, (byte) 0);
    }

    @Override
    public String toString() {
        return "NewSecret[REDACTED]";
    }
}

package com.hope.trading.broker_service.secret.domain;

import java.util.Arrays;

public final class PlainSecret implements AutoCloseable {
    private final byte[] value;

    public PlainSecret(byte[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    public byte[] copyValue() { return Arrays.copyOf(value, value.length); }

    @Override
    public void close() { Arrays.fill(value, (byte) 0); }

    @Override
    public String toString() { return "PlainSecret[REDACTED]"; }
}

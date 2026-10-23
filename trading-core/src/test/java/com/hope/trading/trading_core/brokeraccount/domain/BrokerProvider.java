package com.hope.trading.trading_core.brokeraccount.domain;

import java.util.UUID;

/** Test fixture for the historical Kraken registration reference. */
public final class BrokerProvider {
    public static final UUID KRAKEN = UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01");

    public static UUID[] values() {
        return new UUID[]{KRAKEN};
    }

    private BrokerProvider() {
    }
}

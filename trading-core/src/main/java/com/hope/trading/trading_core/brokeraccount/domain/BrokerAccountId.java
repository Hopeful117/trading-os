package com.hope.trading.trading_core.brokeraccount.domain;

import java.util.Objects;
import java.util.UUID;

public record BrokerAccountId(UUID value) {
    public BrokerAccountId {
        Objects.requireNonNull(value, "value is required");
    }

    public static BrokerAccountId newId() {
        return new BrokerAccountId(UUID.randomUUID());
    }
}

package com.hope.trading.trading_core.execution.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record BrokerOrderId(UUID value) {
    public BrokerOrderId { Objects.requireNonNull(value); }
    public static BrokerOrderId newId() { return new BrokerOrderId(UUID.randomUUID()); }
}

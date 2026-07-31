package com.hope.trading.trading_core.execution.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TradePlanReference(UUID tradePlanId, long version) {
    public TradePlanReference {
        Objects.requireNonNull(tradePlanId);
        if (version < 1) throw new IllegalArgumentException("TradePlan version starts at 1");
    }
}

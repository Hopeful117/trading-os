package com.hope.trading.market_intelligence.domain.ai;

import java.util.Objects;
import java.util.UUID;

public record ContextPackId(UUID value) {
    public ContextPackId {
        Objects.requireNonNull(value, "value");
    }
}

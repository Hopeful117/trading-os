package com.hope.trading.market_intelligence.domain.marketstructure;

import java.time.Instant;
import java.util.Objects;

public record MarketStructureGap(Instant from, Instant to) {
    public MarketStructureGap {
        Objects.requireNonNull(from); Objects.requireNonNull(to);
    }
}

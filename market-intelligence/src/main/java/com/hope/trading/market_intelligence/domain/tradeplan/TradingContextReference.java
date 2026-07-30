package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.*;

public record TradingContextReference(UUID id, long version, Instant snapshotAt) {
    public TradingContextReference {
        Objects.requireNonNull(id); Objects.requireNonNull(snapshotAt);
        if (version < 1) throw new IllegalArgumentException("Context version starts at 1");
    }
}

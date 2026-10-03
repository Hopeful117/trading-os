package com.hope.trading.market_intelligence.domain.marketstructure;

import java.time.Instant;
import java.util.Objects;

public record MarketStructureSourceReference(String sourceId, Instant sourceOccurredAt, Instant fetchedAt) {
    public MarketStructureSourceReference {
        Objects.requireNonNull(sourceId);
    }
}

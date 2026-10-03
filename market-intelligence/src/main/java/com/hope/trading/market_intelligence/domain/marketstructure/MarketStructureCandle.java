package com.hope.trading.market_intelligence.domain.marketstructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record MarketStructureCandle(
        Instant openTime, Instant closeTime, BigDecimal high, BigDecimal low,
        String sourceId, Instant sourceOccurredAt, Instant fetchedAt,
        boolean closed, boolean synthetic) {
    public MarketStructureCandle {
        Objects.requireNonNull(openTime); Objects.requireNonNull(closeTime);
        Objects.requireNonNull(high); Objects.requireNonNull(low);
        Objects.requireNonNull(sourceId);
    }

    public MarketStructureCandle(Instant openTime, Instant closeTime, BigDecimal high, BigDecimal low,
            String sourceId, boolean closed, boolean synthetic) {
        this(openTime, closeTime, high, low, sourceId, null, null, closed, synthetic);
    }
}

package com.hope.trading.market_intelligence.domain.marketstructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketStructureCandle(
        Instant openTime, Instant closeTime, BigDecimal high, BigDecimal low,
        String sourceId, Instant sourceOccurredAt, Instant fetchedAt,
        boolean closed, boolean synthetic, UUID observationId, Instant capturedAt,
         Instant knownAt, String observationFingerprint, String normalizationVersion, String origin) {
    public MarketStructureCandle(
            Instant openTime, Instant closeTime, BigDecimal high, BigDecimal low,
            String sourceId, Instant sourceOccurredAt, Instant fetchedAt,
            boolean closed, boolean synthetic
    ) {
        this(openTime, closeTime, high, low, sourceId, sourceOccurredAt, fetchedAt,
                closed, synthetic, null, null, fetchedAt, null, null, null);
    }

    public MarketStructureCandle {
        Objects.requireNonNull(openTime); Objects.requireNonNull(closeTime);
        Objects.requireNonNull(high); Objects.requireNonNull(low);
        Objects.requireNonNull(sourceId);
    }

    public MarketStructureCandle(Instant openTime, Instant closeTime, BigDecimal high, BigDecimal low,
            String sourceId, boolean closed, boolean synthetic) {
        this(openTime, closeTime, high, low, sourceId, null, null, closed, synthetic,
                null, null, null, null, null, null);
    }
}

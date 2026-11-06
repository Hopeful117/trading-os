package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TrendContextCandle(
        String provider,
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean closed,
        boolean synthetic,
        String sourceId,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        UUID observationId,
        Instant capturedAt,
        Instant knownAt,
        String observationFingerprint,
        String normalizationVersion
) {
    public TrendContextCandle(
            String provider, String symbol, String interval, Instant openTime, Instant closeTime,
            BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume,
            boolean closed, boolean synthetic, String sourceId, Instant sourceOccurredAt,
            Instant fetchedAt
    ) {
        this(provider, symbol, interval, openTime, closeTime, open, high, low, close, volume,
                closed, synthetic, sourceId, sourceOccurredAt, fetchedAt, null, null, fetchedAt,
                null, null);
    }

    public TrendContextCandle {
        provider = required(provider, "provider");
        symbol = required(symbol, "symbol");
        interval = required(interval, "interval");
        Objects.requireNonNull(openTime, "openTime is required");
        Objects.requireNonNull(closeTime, "closeTime is required");
        Objects.requireNonNull(sourceId, "sourceId is required");
        Objects.requireNonNull(fetchedAt, "fetchedAt is required");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

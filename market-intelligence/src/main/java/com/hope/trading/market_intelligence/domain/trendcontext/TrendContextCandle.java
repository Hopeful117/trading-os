package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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
        Instant fetchedAt
) {
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

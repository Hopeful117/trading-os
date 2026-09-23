package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TrendContextSourceReference(
        String source,
        String provider,
        UUID marketId,
        String symbol,
        TrendContextRole role,
        String interval,
        Instant firstCandleTime,
        Instant lastCandleTime,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        String sourceSnapshot,
        String contentDigest
) {
    public TrendContextSourceReference {
        source = required(source, "source");
        provider = required(provider, "provider");
        Objects.requireNonNull(marketId, "marketId is required");
        symbol = required(symbol, "symbol");
        Objects.requireNonNull(role, "role is required");
        interval = required(interval, "interval");
        fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt is required");
        sourceSnapshot = sourceSnapshot == null ? "" : sourceSnapshot;
        contentDigest = contentDigest == null ? "" : contentDigest;
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

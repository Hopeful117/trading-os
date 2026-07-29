package com.hope.trading.market_intelligence.domain.observation;

import java.time.Instant;
import java.util.Objects;

public record RawMarketDataReference(
        String source, String instrument, String timeframe, String fingerprint, Instant observedAt
) {
    public RawMarketDataReference {
        source = required(source, "source");
        instrument = required(instrument, "instrument");
        timeframe = required(timeframe, "timeframe");
        fingerprint = required(fingerprint, "fingerprint");
        Objects.requireNonNull(observedAt, "observedAt");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}

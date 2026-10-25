package com.hope.trading.market_intelligence.domain.thesis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record MarketThesisLevel(
        String reference,
        String role,
        String timeframe,
        BigDecimal value,
        String source,
        String sourceReference,
        Instant observedAt,
        Instant confirmationAt,
        Instant sourceCutoff
) {
    public MarketThesisLevel(
            String reference, String role, String timeframe, BigDecimal value) {
        this(reference, role, timeframe, value, "UNKNOWN", reference,
                null, null, null);
    }

    public MarketThesisLevel {
        reference = required(reference, "reference");
        role = required(role, "role");
        timeframe = required(timeframe, "timeframe");
        Objects.requireNonNull(value, "value");
        source = source == null ? "UNKNOWN" : required(source, "source");
        sourceReference = sourceReference == null ? reference : required(sourceReference, "sourceReference");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}

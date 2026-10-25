package com.hope.trading.market_intelligence.domain.thesis;

import java.math.BigDecimal;
import java.util.Objects;

public record MarketThesisLevel(
        String reference,
        String role,
        String timeframe,
        BigDecimal value
) {
    public MarketThesisLevel {
        reference = required(reference, "reference");
        role = required(role, "role");
        timeframe = required(timeframe, "timeframe");
        Objects.requireNonNull(value, "value");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}

package com.hope.trading.market_intelligence.domain.momentum;

import java.util.Objects;

public record MomentumPolicy(
        String version,
        int lookbackBars,
        int comparisonWindowBars
) {
    public static final MomentumPolicy V1 = new MomentumPolicy("ohlc-momentum/v1", 8, 4);

    public MomentumPolicy {
        version = required(version, "version");
        if (lookbackBars < 4) throw new IllegalArgumentException("lookbackBars must be at least 4");
        if (comparisonWindowBars < 2 || comparisonWindowBars * 2 > lookbackBars) {
            throw new IllegalArgumentException("comparisonWindowBars must fit two comparison windows");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Instant;
import java.util.Objects;

public record TrendContextGapFinding(
        TrendContextRole role,
        String code,
        Instant from,
        Instant to,
        String detail
) {
    public TrendContextGapFinding {
        Objects.requireNonNull(role, "role is required");
        code = required(code, "code");
        detail = required(detail, "detail");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

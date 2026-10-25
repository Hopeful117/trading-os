package com.hope.trading.market_intelligence.domain.thesis;

import java.util.Objects;

public record MarketThesisCondition(
        String code,
        MarketThesisConditionStatus status,
        String sourceReference,
        String explanation
) {
    public MarketThesisCondition {
        code = required(code, "code");
        Objects.requireNonNull(status, "status");
        sourceReference = required(sourceReference, "sourceReference");
        explanation = required(explanation, "explanation");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}

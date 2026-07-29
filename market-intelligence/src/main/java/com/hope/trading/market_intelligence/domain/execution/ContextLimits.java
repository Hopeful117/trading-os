package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.context.ContextClassification;

public record ContextLimits(
        int maximumSections,
        int maximumDataPoints,
        int maximumHistoricalDepth,
        int maximumTimeframes,
        ContextClassification maximumClassification
) {
    public ContextLimits {
        if (maximumSections < 1 || maximumDataPoints < 1
                || maximumHistoricalDepth < 0 || maximumTimeframes < 1) {
            throw new IllegalArgumentException("Context limits must be positive");
        }
        if (maximumClassification == null) {
            throw new IllegalArgumentException("Maximum classification is required");
        }
    }
}

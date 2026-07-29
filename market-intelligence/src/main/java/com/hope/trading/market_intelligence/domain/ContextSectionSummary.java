package com.hope.trading.market_intelligence.domain;

import java.time.Instant;

public record ContextSectionSummary(
        ContextSectionType type,
        ContextSectionStatus status,
        ContextSensitivity sensitivity,
        String source,
        Instant sourceOccurredAt,
        String message
) {
}

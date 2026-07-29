package com.hope.trading.market_intelligence.domain;

import java.time.Instant;

public record ContextProvenance(
        String source,
        Instant sourceOccurredAt,
        Instant fetchedAt
) {
}

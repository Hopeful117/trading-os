package com.hope.trading.market_intelligence.domain;

import java.time.Instant;

public record ContextProvenance(
        String source,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        String sourceReference,
        String sourceVersion,
        String normalizationVersion
) {
    public ContextProvenance(String source, Instant sourceOccurredAt, Instant fetchedAt) {
        this(source, sourceOccurredAt, fetchedAt, null, null, null);
    }
}

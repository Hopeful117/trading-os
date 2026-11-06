package com.hope.trading.market_intelligence.domain.marketstructure;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketStructureSourceReference(
        String sourceId,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        UUID observationId,
        Instant capturedAt,
        Instant knownAt,
        String observationFingerprint,
        String normalizationVersion
) {
    public MarketStructureSourceReference(String sourceId, Instant sourceOccurredAt, Instant fetchedAt) {
        this(sourceId, sourceOccurredAt, fetchedAt, null, null, fetchedAt, null, null);
    }

    public MarketStructureSourceReference {
        Objects.requireNonNull(sourceId);
    }
}

package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderBookEvidenceSegment(
        UUID segmentId,
        UUID marketId,
        String provider,
        String providerSymbol,
        int depth,
        UUID sequenceId,
        long startOrdinal,
        Long endOrdinal,
        Instant startKnownAt,
        Instant endKnownAt,
        UUID successorEvidenceId,
        OrderBookEvidenceOrigin successorOrigin,
        OrderBookEvidenceSegmentStatus status,
        OrderBookEvidenceSegmentClassification classification,
        Instant createdAt,
        Instant closedAt
) {
    public OrderBookEvidenceSegment {
        Objects.requireNonNull(segmentId, "segmentId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(sequenceId, "sequenceId is required");
        Objects.requireNonNull(startKnownAt, "startKnownAt is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(classification, "classification is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (depth < 1 || startOrdinal < 0) {
            throw new IllegalArgumentException("segment depth and start ordinal must be positive");
        }
        if (endOrdinal == null && status != OrderBookEvidenceSegmentStatus.OPEN) {
            throw new IllegalArgumentException("non-open segments require an end ordinal");
        }
        if (endOrdinal != null && endOrdinal < startOrdinal) {
            throw new IllegalArgumentException("segment end ordinal cannot precede start ordinal");
        }
        if ((successorEvidenceId == null) != (successorOrigin == null)) {
            throw new IllegalArgumentException("segment successor identity and origin must be provided together");
        }
        if (status == OrderBookEvidenceSegmentStatus.OPEN && closedAt != null) {
            throw new IllegalArgumentException("open segments cannot have a closed timestamp");
        }
        if (status != OrderBookEvidenceSegmentStatus.OPEN && closedAt == null) {
            throw new IllegalArgumentException("closed, retiring and retired segments require a closed timestamp");
        }
    }

    public boolean hasTrustedSuccessorBoundary() {
        return successorEvidenceId != null && successorOrigin != null;
    }
}

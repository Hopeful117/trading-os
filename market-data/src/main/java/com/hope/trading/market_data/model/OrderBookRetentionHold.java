package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderBookRetentionHold(
        UUID holdId,
        String holdKey,
        UUID artifactId,
        UUID sequenceId,
        UUID segmentId,
        String reason,
        Instant createdAt,
        Instant releasedAt
) {
    public OrderBookRetentionHold {
        Objects.requireNonNull(holdId, "holdId is required");
        Objects.requireNonNull(holdKey, "holdKey is required");
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (artifactId == null && sequenceId == null && segmentId == null) {
            throw new IllegalArgumentException("an artifact, sequence or segment target is required");
        }
    }

    public OrderBookRetentionHold(UUID holdId, String holdKey, UUID artifactId, UUID sequenceId,
                                  String reason, Instant createdAt, Instant releasedAt) {
        this(holdId, holdKey, artifactId, sequenceId, null, reason, createdAt, releasedAt);
    }

    public boolean active() {
        return releasedAt == null;
    }

    public boolean targetsSegment() {
        return segmentId != null;
    }
}

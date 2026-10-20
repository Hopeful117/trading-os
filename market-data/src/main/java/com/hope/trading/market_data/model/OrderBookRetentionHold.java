package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderBookRetentionHold(
        UUID holdId,
        String holdKey,
        UUID artifactId,
        UUID sequenceId,
        String reason,
        Instant createdAt,
        Instant releasedAt
) {
    public OrderBookRetentionHold {
        Objects.requireNonNull(holdId, "holdId is required");
        Objects.requireNonNull(holdKey, "holdKey is required");
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (artifactId == null && sequenceId == null) {
            throw new IllegalArgumentException("an artifact or sequence target is required");
        }
    }

    public boolean active() {
        return releasedAt == null;
    }
}

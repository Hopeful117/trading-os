package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderBookEvidenceDependency(
        UUID dependencyId,
        String dependencyKey,
        String consumerType,
        String consumerIdentity,
        UUID artifactId,
        UUID sequenceId,
        OrderBookDependencyState state,
        String reason,
        Instant createdAt,
        Instant releasedAt
) {
    public OrderBookEvidenceDependency {
        Objects.requireNonNull(dependencyId, "dependencyId is required");
        Objects.requireNonNull(dependencyKey, "dependencyKey is required");
        Objects.requireNonNull(consumerType, "consumerType is required");
        Objects.requireNonNull(consumerIdentity, "consumerIdentity is required");
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (artifactId == null && sequenceId == null) {
            throw new IllegalArgumentException("an artifact or sequence target is required");
        }
        if (state == OrderBookDependencyState.RELEASED && releasedAt == null) {
            throw new IllegalArgumentException("releasedAt is required for released dependencies");
        }
    }
}

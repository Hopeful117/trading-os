package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.UUID;

public record OrderBookRetirementCandidate(
        UUID sequenceId,
        UUID marketId,
        int depth,
        OrderBookSequenceStatus sequenceStatus,
        long evidenceCount,
        long firstOrdinal,
        long lastOrdinal,
        Instant firstKnownAt,
        Instant lastKnownAt,
        Instant invalidatedAt
) {
}

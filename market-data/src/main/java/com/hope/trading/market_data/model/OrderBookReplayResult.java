package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderBookReplayResult(
        ReplayStatus status,
        UUID marketId,
        int depth,
        UUID sequenceId,
        Instant evaluationCutoff,
        Instant latestOccurredAt,
        Instant latestKnownAt,
        Long sourceStartOrdinal,
        Long sourceEndOrdinal,
        String provider,
        String providerSymbol,
        String normalizationVersion,
        String sourceEvidenceFingerprint,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        String reason
) {
    public enum ReplayStatus {
        AVAILABLE,
        INVALID,
        UNAVAILABLE
    }

    public static OrderBookReplayResult unavailable(UUID marketId, int depth, Instant cutoff, String reason) {
        return new OrderBookReplayResult(ReplayStatus.UNAVAILABLE, marketId, depth, null, cutoff,
                null, null, null, null, null, null, null, null, List.of(), List.of(), reason);
    }

    public static OrderBookReplayResult invalid(UUID marketId, int depth, Instant cutoff, UUID sequenceId,
                                                String reason) {
        return new OrderBookReplayResult(ReplayStatus.INVALID, marketId, depth, sequenceId, cutoff,
                null, null, null, null, null, null, null, null, List.of(), List.of(), reason);
    }
}

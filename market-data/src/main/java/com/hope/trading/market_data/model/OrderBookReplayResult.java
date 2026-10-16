package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderBookReplayResult(
        ReplayStatus status,
        UUID sequenceId,
        Instant evaluationCutoff,
        Instant latestKnownAt,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        String reason
) {
    public enum ReplayStatus {
        AVAILABLE,
        UNAVAILABLE
    }

    public static OrderBookReplayResult unavailable(Instant cutoff, String reason) {
        return new OrderBookReplayResult(ReplayStatus.UNAVAILABLE, null, cutoff, null,
                List.of(), List.of(), reason);
    }
}

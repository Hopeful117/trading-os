package com.hope.trading.trading_core.execution.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HistoricalOrderBookPort {
    Replay replay(UUID marketId, int depth, Instant executionCutoff);

    record Replay(
            Status status,
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
            List<Level> bids,
            List<Level> asks,
            String reason
    ) {
        public Replay {
            status = status == null ? Status.UNAVAILABLE : status;
            bids = bids == null ? List.of() : List.copyOf(bids);
            asks = asks == null ? List.of() : List.copyOf(asks);
        }
    }

    record Level(BigDecimal price, BigDecimal quantity) {
    }

    enum Status {
        AVAILABLE,
        INVALID,
        UNAVAILABLE
    }
}

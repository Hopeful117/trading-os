package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderBookContext(
        UUID marketId,
        int depth,
        ReplayStatus status,
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
) implements ContextPayload {
    public OrderBookContext {
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff is required");
        if (depth < 1) throw new IllegalArgumentException("depth must be positive");
        bids = ordered(bids, true);
        asks = ordered(asks, false);
        if (status == ReplayStatus.AVAILABLE) {
            Objects.requireNonNull(sequenceId, "sequenceId is required for available replay");
            Objects.requireNonNull(latestOccurredAt, "latestOccurredAt is required for available replay");
            Objects.requireNonNull(latestKnownAt, "latestKnownAt is required for available replay");
            Objects.requireNonNull(normalizationVersion, "normalizationVersion is required for available replay");
            Objects.requireNonNull(sourceEvidenceFingerprint, "sourceEvidenceFingerprint is required for available replay");
        }
    }

    private static List<Level> ordered(List<Level> values, boolean descending) {
        List<Level> ordered = (values == null ? List.<Level>of() : values).stream()
                .map(Level::validated).toList();
        for (int index = 1; index < ordered.size(); index++) {
            int comparison = ordered.get(index - 1).price().compareTo(ordered.get(index).price());
            if ((descending && comparison < 0) || (!descending && comparison > 0)) {
                throw new IllegalArgumentException("Order Book levels are not normalized");
            }
        }
        return List.copyOf(ordered);
    }

    public enum ReplayStatus {
        AVAILABLE,
        INVALID,
        UNAVAILABLE
    }

    public record Level(BigDecimal price, BigDecimal quantity) {
        public Level {
            validate(price, quantity);
        }

        private static void validate(Level level) {
            Objects.requireNonNull(level, "level is required");
            validate(level.price(), level.quantity());
        }

        private static Level validated(Level level) {
            validate(level);
            return level;
        }

        private static void validate(BigDecimal price, BigDecimal quantity) {
            Objects.requireNonNull(price, "level price is required");
            Objects.requireNonNull(quantity, "level quantity is required");
            if (price.signum() <= 0 || quantity.signum() < 0) {
                throw new IllegalArgumentException("Order Book level must have a positive price and non-negative quantity");
            }
        }
    }
}

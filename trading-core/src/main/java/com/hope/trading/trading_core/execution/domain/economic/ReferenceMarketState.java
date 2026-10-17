package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReferenceMarketState(
        UUID marketId,
        String instrument,
        Instant evaluationCutoff,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal referencePrice,
        Instant knownAt,
        String evidenceFingerprint
) {
    public ReferenceMarketState {
        Objects.requireNonNull(marketId, "marketId is required");
        instrument = required(instrument, "instrument");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff is required");
        requirePositive(bid, "bid");
        requirePositive(ask, "ask");
        requirePositive(referencePrice, "referencePrice");
        Objects.requireNonNull(knownAt, "knownAt is required");
        evidenceFingerprint = required(evidenceFingerprint, "evidenceFingerprint");
        if (bid.compareTo(ask) > 0) throw new IllegalArgumentException("bid cannot exceed ask");
        if (referencePrice.compareTo(bid) < 0 || referencePrice.compareTo(ask) > 0) {
            throw new IllegalArgumentException("referencePrice must be inside the quoted spread");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (Objects.requireNonNull(value, field).signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}

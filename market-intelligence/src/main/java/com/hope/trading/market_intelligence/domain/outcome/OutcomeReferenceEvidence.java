package com.hope.trading.market_intelligence.domain.outcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record OutcomeReferenceEvidence(
        BigDecimal close,
        Instant closeTime,
        Instant knownAt,
        String observationFingerprint,
        String normalizationVersion
) {
    public OutcomeReferenceEvidence {
        Objects.requireNonNull(close, "reference close is required");
        Objects.requireNonNull(closeTime, "reference close time is required");
        Objects.requireNonNull(knownAt, "reference known-at is required");
        Objects.requireNonNull(observationFingerprint, "reference fingerprint is required");
        Objects.requireNonNull(normalizationVersion, "reference normalization version is required");
        if (close.signum() <= 0) {
            throw new IllegalArgumentException("reference close must be positive");
        }
    }
}

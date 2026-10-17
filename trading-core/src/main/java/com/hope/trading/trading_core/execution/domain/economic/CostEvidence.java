package com.hope.trading.trading_core.execution.domain.economic;

import java.time.Instant;
import java.util.Objects;

public record CostEvidence(
        String fingerprint,
        String sourceId,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant knownAt
) {
    public CostEvidence {
        fingerprint = required(fingerprint, "fingerprint");
        sourceId = required(sourceId, "sourceId");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("effectiveFrom must precede effectiveTo");
        }
        Objects.requireNonNull(knownAt, "knownAt is required");
    }

    public boolean eligibleAt(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff is required");
        return !knownAt.isAfter(cutoff)
                && !effectiveFrom.isAfter(cutoff)
                && (effectiveTo == null || effectiveTo.isAfter(cutoff));
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}

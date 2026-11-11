package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalClosureEvidence(
        UUID evidenceId,
        UUID marketId,
        String canonicalSymbol,
        OhlcInterval interval,
        String venue,
        String closureType,
        String reason,
        Instant closureStartUtc,
        Instant closureEndUtc,
        String sourceId,
        String sourceVersion,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant knownAt,
        String fingerprint
) {
    public HistoricalClosureEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        requireText(canonicalSymbol, "canonicalSymbol");
        Objects.requireNonNull(interval, "interval is required");
        requireText(venue, "venue");
        requireText(closureType, "closureType");
        requireText(reason, "reason");
        Objects.requireNonNull(closureStartUtc, "closureStartUtc is required");
        Objects.requireNonNull(closureEndUtc, "closureEndUtc is required");
        requireText(sourceId, "sourceId");
        requireText(sourceVersion, "sourceVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(effectiveTo, "effectiveTo is required");
        Objects.requireNonNull(knownAt, "knownAt is required");
        requireText(fingerprint, "fingerprint");
        if (!closureStartUtc.isBefore(closureEndUtc)) {
            throw new IllegalArgumentException("closureStartUtc must be before closureEndUtc");
        }
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("effectiveFrom must be before effectiveTo");
        }
        if (closureStartUtc.isBefore(effectiveFrom) || closureEndUtc.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("closure must be inside its effective interval");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}

package com.hope.trading.market_intelligence.domain.replay;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReplayEvidenceSample(
        UUID observationId,
        String provider,
        String sourceId,
        String normalizationVersion,
        Instant openTime,
        Instant closeTime,
        Instant fetchedAt,
        Instant capturedAt,
        Instant knownAt,
        Instant sourceUpdatedAt,
        boolean closed,
        boolean synthetic,
        String observationFingerprint
) {
    public ReplayEvidenceSample {
        Objects.requireNonNull(observationId, "observation ID is required");
        requireText(provider, "provider");
        requireText(sourceId, "source ID");
        requireText(normalizationVersion, "normalization version");
        Objects.requireNonNull(openTime, "open time is required");
        Objects.requireNonNull(closeTime, "close time is required");
        Objects.requireNonNull(fetchedAt, "fetched-at is required");
        Objects.requireNonNull(capturedAt, "captured-at is required");
        Objects.requireNonNull(knownAt, "known-at is required");
        requireText(observationFingerprint, "observation fingerprint");
    }

    public boolean admissibleAt(Instant cutoff) {
        return closed
                && !synthetic
                && !closeTime.isAfter(cutoff)
                && !knownAt.isAfter(cutoff)
                && (sourceUpdatedAt == null || !sourceUpdatedAt.isAfter(cutoff));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

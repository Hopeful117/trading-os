package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalContinuityPolicy(
        String policyVersion,
        UUID marketId,
        String canonicalSymbol,
        String venue,
        OhlcInterval interval,
        Instant evidenceCutoff,
        boolean allowVerifiedClosures
) {
    public HistoricalContinuityPolicy(String policyVersion, OhlcInterval interval,
                                      Instant evidenceCutoff, boolean allowVerifiedClosures) {
        this(policyVersion, null, null, null, interval, evidenceCutoff, allowVerifiedClosures);
    }

    public HistoricalContinuityPolicy {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion is required");
        }
        if (canonicalSymbol != null && canonicalSymbol.isBlank()) {
            throw new IllegalArgumentException("canonicalSymbol cannot be blank");
        }
        if (venue != null && venue.isBlank()) {
            throw new IllegalArgumentException("venue cannot be blank");
        }
        Objects.requireNonNull(interval, "interval is required");
        Objects.requireNonNull(evidenceCutoff, "evidenceCutoff is required");
    }
}

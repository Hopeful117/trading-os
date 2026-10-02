package com.hope.trading.market_intelligence.strategy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity of the evidence consumed by a strategy evaluation. */
public record StrategyEvidenceProvenance(
        UUID observationId,
        UUID observationLineageId,
        long observationVersion,
        Instant cutOffAt,
        String profileVersion,
        String ruleVersion,
        String inputFingerprint,
        String assessmentFingerprint
) {
    public StrategyEvidenceProvenance {
        Objects.requireNonNull(observationId, "observationId is required");
        Objects.requireNonNull(observationLineageId, "observationLineageId is required");
        if (observationVersion < 1) {
            throw new IllegalArgumentException("observationVersion starts at 1");
        }
        Objects.requireNonNull(cutOffAt, "cutOffAt is required");
        requireText(profileVersion, "profileVersion");
        requireText(ruleVersion, "ruleVersion");
        requireText(inputFingerprint, "inputFingerprint");
        requireText(assessmentFingerprint, "assessmentFingerprint");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

package com.hope.trading.market_intelligence.domain.outcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ObservedMarketOutcome(
        UUID marketId,
        Instant evaluationCutoff,
        OutcomeHorizon horizon,
        Instant maturityAt,
        ObservedMarketOutcomeStatus status,
        String reason,
        BigDecimal referencePrice,
        String referenceObservationFingerprint,
        BigDecimal finalPrice,
        BigDecimal futureHigh,
        BigDecimal futureLow,
        BigDecimal forwardReturn,
        BigDecimal futureHighReturn,
        BigDecimal futureLowReturn,
        BigDecimal futureRange,
        int expectedObservationCount,
        int observedObservationCount,
        String referenceNormalizationVersion,
        java.util.List<String> futureNormalizationVersions,
        String futureEvidenceFingerprint,
        String outcomeDefinitionVersion,
        String outcomeFingerprint,
        Instant computedAt,
        UUID analysisExecutionId
) {
    public ObservedMarketOutcome {
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(horizon, "horizon is required");
        Objects.requireNonNull(maturityAt, "maturity is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(outcomeDefinitionVersion, "definition version is required");
        Objects.requireNonNull(outcomeFingerprint, "outcome fingerprint is required");
        Objects.requireNonNull(computedAt, "computed-at is required");
        futureNormalizationVersions = futureNormalizationVersions == null
                ? java.util.List.of() : java.util.List.copyOf(futureNormalizationVersions);
        if (expectedObservationCount < 0 || observedObservationCount < 0
                || observedObservationCount > expectedObservationCount) {
            throw new IllegalArgumentException("invalid outcome observation counts");
        }
        if (status == ObservedMarketOutcomeStatus.AVAILABLE
                && (referencePrice == null || finalPrice == null || futureHigh == null || futureLow == null
                || forwardReturn == null || futureHighReturn == null || futureLowReturn == null
                 || futureRange == null || referenceObservationFingerprint == null
                 || referenceNormalizationVersion == null || futureNormalizationVersions.isEmpty()
                 || futureEvidenceFingerprint == null)) {
            throw new IllegalArgumentException("available outcome requires complete metrics and provenance");
        }
        if (status != ObservedMarketOutcomeStatus.AVAILABLE
                && (finalPrice != null || futureHigh != null || futureLow != null
                || forwardReturn != null || futureHighReturn != null || futureLowReturn != null
                || futureRange != null)) {
            throw new IllegalArgumentException("non-available outcome cannot contain final metrics");
        }
        if (status == ObservedMarketOutcomeStatus.PENDING
                && !computedAt.isBefore(maturityAt)) {
            throw new IllegalArgumentException("pending outcome must be before maturity");
        }
    }
}

package com.hope.trading.market_intelligence.domain.outcome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MarketOutcomeCalculator {
    public static final String DEFINITION_VERSION = "market-outcome-v1";
    private static final int SCALE = 18;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Public calculation contract retained for deterministic replay callers. */
    @SuppressWarnings("java:S107")
    public CalculatedOutcome calculate(
            OutcomeReferenceEvidence reference,
            List<OutcomeFutureObservation> futureObservations,
            int expectedObservationCount,
            int observedObservationCount,
            List<String> gaps,
            String futureEvidenceFingerprint,
            Instant evaluationCutoff,
            OutcomeHorizon horizon
    ) {
        Objects.requireNonNull(reference, "reference is required");
        Objects.requireNonNull(futureObservations, "future observations are required");
        Objects.requireNonNull(gaps, "gaps are required");
        Objects.requireNonNull(futureEvidenceFingerprint, "future fingerprint is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(horizon, "horizon is required");
        if (expectedObservationCount <= 0 || observedObservationCount != expectedObservationCount
                || futureObservations.size() != observedObservationCount || !gaps.isEmpty()) {
            throw new IllegalArgumentException("complete future evidence is required");
        }

        List<OutcomeFutureObservation> ordered = futureObservations.stream()
                .sorted(Comparator.comparing(OutcomeFutureObservation::openTime))
                .toList();
        BigDecimal referencePrice = reference.close();
        BigDecimal finalPrice = ordered.getLast().close();
        BigDecimal futureHigh = ordered.stream().map(OutcomeFutureObservation::high)
                .max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal futureLow = ordered.stream().map(OutcomeFutureObservation::low)
                .min(Comparator.naturalOrder()).orElseThrow();

        return new CalculatedOutcome(
                referencePrice,
                finalPrice,
                futureHigh,
                futureLow,
                fraction(finalPrice.subtract(referencePrice), referencePrice),
                fraction(futureHigh.subtract(referencePrice), referencePrice),
                fraction(futureLow.subtract(referencePrice), referencePrice),
                fraction(futureHigh.subtract(futureLow), referencePrice),
                DEFINITION_VERSION
        );
    }

    private BigDecimal fraction(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, SCALE, ROUNDING);
    }

    public record CalculatedOutcome(
            BigDecimal referencePrice,
            BigDecimal finalPrice,
            BigDecimal futureHigh,
            BigDecimal futureLow,
            BigDecimal forwardReturn,
            BigDecimal futureHighReturn,
            BigDecimal futureLowReturn,
            BigDecimal futureRange,
            String definitionVersion
    ) { }
}

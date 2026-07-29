package com.hope.trading.market_intelligence.domain.observation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ObservationEvidence(
        UUID evidenceId,
        String origin,
        String title,
        String explanation,
        Map<String, BigDecimal> measurements,
        Map<String, BigDecimal> thresholds,
        Instant observedAt,
        BigDecimal confidenceContribution,
        CapabilityResultTrace capabilityResult
) {
    public ObservationEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId");
        origin = required(origin, "origin");
        title = required(title, "title");
        explanation = required(explanation, "explanation");
        measurements = Map.copyOf(measurements);
        thresholds = Map.copyOf(thresholds);
        Objects.requireNonNull(observedAt, "observedAt");
        confidenceContribution = Objects.requireNonNull(
                confidenceContribution, "confidenceContribution");
        if (confidenceContribution.signum() < 0
                || confidenceContribution.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Contribution must be between 0 and 1");
        }
        Objects.requireNonNull(capabilityResult, "capabilityResult");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}

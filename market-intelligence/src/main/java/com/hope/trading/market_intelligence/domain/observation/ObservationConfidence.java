package com.hope.trading.market_intelligence.domain.observation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record ObservationConfidence(BigDecimal score, String calculationMethod) {
    public static final String METHOD = "EVIDENCE_ARITHMETIC_MEAN_V1";

    public ObservationConfidence {
        score = Objects.requireNonNull(score, "score").setScale(4, RoundingMode.HALF_UP);
        if (score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence score must be between 0 and 1");
        }
        calculationMethod = Objects.requireNonNull(calculationMethod, "calculationMethod");
    }

    public static ObservationConfidence from(List<ObservationEvidence> evidence) {
        List<ObservationEvidence> copy = List.copyOf(evidence);
        if (copy.isEmpty()) throw new IllegalArgumentException("Evidence is required");
        BigDecimal total = copy.stream()
                .map(ObservationEvidence::confidenceContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ObservationConfidence(
                total.divide(BigDecimal.valueOf(copy.size()), 4, RoundingMode.HALF_UP), METHOD);
    }
}

package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.domain.observation.ObservationType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ObservationRuleResult(
        ObservationType type,
        String title,
        String explanation,
        Set<String> categories,
        String horizon,
        Instant validFrom,
        Instant validUntil,
        List<ObservationEvidenceCandidate> evidence
) {
    public ObservationRuleResult {
        categories = Set.copyOf(categories);
        evidence = List.copyOf(evidence);
    }
}

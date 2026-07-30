package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Instant;
import java.util.*;

public record OpportunityFusionResult(
        OpportunityType type,
        OpportunityScore score,
        String explanation,
        Set<ObservationReference> observations,
        Set<AiAnalysisReference> aiAnalyses,
        Instant validFrom,
        Instant validUntil
) {
    public OpportunityFusionResult {
        Objects.requireNonNull(type);
        Objects.requireNonNull(score);
        explanation = Objects.requireNonNull(explanation).trim();
        if (explanation.isEmpty()) throw new IllegalArgumentException("explanation is required");
        observations = Set.copyOf(observations);
        if (observations.isEmpty()) throw new IllegalArgumentException("Observations are required");
        aiAnalyses = Set.copyOf(aiAnalyses);
        Objects.requireNonNull(validFrom);
    }
}

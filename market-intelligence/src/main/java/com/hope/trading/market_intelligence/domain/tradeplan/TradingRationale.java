package com.hope.trading.market_intelligence.domain.tradeplan;

import com.hope.trading.market_intelligence.domain.opportunity.*;
import java.util.*;

public record TradingRationale(
        Set<OpportunityPlanReference> opportunities,
        Set<ObservationReference> observations,
        Set<AiAnalysisReference> aiAnalyses,
        String thesis,
        Set<String> confirmationConditions,
        Set<String> invalidationConditions
) {
    public TradingRationale {
        opportunities = Set.copyOf(opportunities);
        if (opportunities.isEmpty()) throw new IllegalArgumentException("Opportunity is required");
        observations = Set.copyOf(observations);
        if (observations.isEmpty()) throw new IllegalArgumentException("Observation is required");
        aiAnalyses = Set.copyOf(aiAnalyses);
        thesis = Objects.requireNonNull(thesis).trim();
        if (thesis.isEmpty()) throw new IllegalArgumentException("thesis is required");
        confirmationConditions = Set.copyOf(confirmationConditions);
        invalidationConditions = Set.copyOf(invalidationConditions);
        if (confirmationConditions.isEmpty() || invalidationConditions.isEmpty()) {
            throw new IllegalArgumentException("Confirmation and invalidation rules are required");
        }
    }
}

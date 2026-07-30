package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Default policy: deterministic Observation confidence drives business priority.
 * AI references remain traceable but do not alter this baseline.
 */
public final class DeterministicOpportunityFusionPolicy implements OpportunityFusionPolicy {
    @Override
    public OpportunityFusionResult fuse(
            CreateOpportunityCommand command, List<Observation> observations,
            Set<AiAnalysisReference> aiAnalyses
    ) {
        BigDecimal average = observations.stream()
                .map(item -> item.confidence().score())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(observations.size()), 4, java.math.RoundingMode.HALF_UP);
        OpportunityType type = switch (command.timeframe().toLowerCase(Locale.ROOT)) {
            case "1m", "5m" -> OpportunityType.SCALPING;
            case "15m", "30m", "1h", "4h" -> OpportunityType.INTRADAY;
            case "1d", "1w" -> OpportunityType.SWING;
            default -> OpportunityType.POSITIONAL;
        };
        Set<ObservationReference> selected = observations.stream()
                .map(item -> new ObservationReference(item.id()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new OpportunityFusionResult(
                type, new OpportunityScore(average.multiply(BigDecimal.valueOf(100))),
                command.scenario(), selected, aiAnalyses, command.evaluatedAt(),
                command.validUntil());
    }
}

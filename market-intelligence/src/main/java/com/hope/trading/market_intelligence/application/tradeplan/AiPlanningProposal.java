package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.TradeDirection;
import java.util.*;

public record AiPlanningProposal(
        String instrument, TradeDirection direction,
        List<PlanningContribution> contributions, Set<UUID> sourceAnalysisIds
) {
    public AiPlanningProposal {
        instrument = instrument == null ? "" : instrument.trim();
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
        sourceAnalysisIds = sourceAnalysisIds == null ? Set.of() : Set.copyOf(sourceAnalysisIds);
    }
    public static AiPlanningProposal empty(PlanningInput input) {
        return new AiPlanningProposal(
                input.instrument(), input.direction(), List.of(), Set.of());
    }
}

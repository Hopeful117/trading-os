package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.List;
import java.util.Set;

public interface OpportunityFusionPolicy {
    OpportunityFusionResult fuse(
            CreateOpportunityCommand command,
            List<Observation> observations,
            Set<AiAnalysisReference> aiAnalyses);
}

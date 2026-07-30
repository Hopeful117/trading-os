package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.opportunity.AiAnalysisReference;

import java.util.Set;

/** Read-only knowledge catalog. It never executes an AI model. */
public interface AiAnalysisCatalog {
    boolean allExist(Set<AiAnalysisReference> references);
}

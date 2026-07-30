package com.hope.trading.market_intelligence.adapter.ai;

import com.hope.trading.market_intelligence.application.port.AiAnalysisCatalog;
import com.hope.trading.market_intelligence.domain.opportunity.AiAnalysisReference;

import java.util.Set;

/** Empty references are valid; configured AI references require a future AI Analysis store. */
public final class ReadOnlyAiAnalysisCatalog implements AiAnalysisCatalog {
    @Override public boolean allExist(Set<AiAnalysisReference> references) {
        return references.isEmpty();
    }
}

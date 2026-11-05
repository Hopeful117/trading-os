package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.AnalysisOrigin;

public interface AiAnalysisCapability extends AnalysisCapability {
    default CapabilityAnalysisResult analyzeAdaptive(
            com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest request,
            com.hope.trading.market_intelligence.domain.IntelligenceContext context) {
        return analyze(request, context);
    }

    @Override
    default AnalysisOrigin origin() {
        return AnalysisOrigin.AI;
    }
}

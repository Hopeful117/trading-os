package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.AnalysisOrigin;

public interface AiAnalysisCapability extends AnalysisCapability {
    @Override
    default AnalysisOrigin origin() {
        return AnalysisOrigin.AI;
    }
}

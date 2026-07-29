package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;

public interface AiEnginePort {
    boolean available();

    CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request,
            IntelligenceContext context
    );
}

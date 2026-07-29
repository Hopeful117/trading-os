package com.hope.trading.market_intelligence.adapter.ai;

import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;
import com.hope.trading.market_intelligence.application.port.AiEnginePort;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import org.springframework.stereotype.Component;

@Component
public class DisabledAiEngineAdapter implements AiEnginePort {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request,
            IntelligenceContext context
    ) {
        throw new IllegalStateException("AI Engine is not configured");
    }
}

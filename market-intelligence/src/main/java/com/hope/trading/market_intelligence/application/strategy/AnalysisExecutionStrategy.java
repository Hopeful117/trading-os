package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;

public interface AnalysisExecutionStrategy {
    AnalysisExecutionMode mode();

    AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request);
}

package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;

import java.util.UUID;

public interface AnalysisExecutionDispatcher {
    void dispatch(UUID executionId, IntelligenceAnalysisRequest request);

    void cancel(UUID executionId);
}

package com.hope.trading.market_intelligence.domain;

import java.util.UUID;

public record IntelligenceAnalysisRequest(
        UUID analysisId,
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId
) {
    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective) {
        this(analysisId, marketId, mode, objective, null, null, null);
    }
}

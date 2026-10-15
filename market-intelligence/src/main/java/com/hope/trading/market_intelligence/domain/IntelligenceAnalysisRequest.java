package com.hope.trading.market_intelligence.domain;

import java.util.UUID;
import java.time.Instant;

public record IntelligenceAnalysisRequest(
        UUID analysisId,
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId,
        Instant evaluationCutoff
) {
    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective) {
        this(analysisId, marketId, mode, objective, null, null, null, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId,
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId
    ) {
        this(analysisId, marketId, mode, objective,
                accountId, scanId, scanMarketId, null);
    }

    public IntelligenceAnalysisRequest withEvaluationCutoff(Instant cutoff) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(),
                scanMarketId(), cutoff);
    }
}

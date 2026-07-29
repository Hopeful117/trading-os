package com.hope.trading.market_intelligence.domain;

import java.util.UUID;

public record IntelligenceAnalysisRequest(
        UUID analysisId,
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective
) {
}

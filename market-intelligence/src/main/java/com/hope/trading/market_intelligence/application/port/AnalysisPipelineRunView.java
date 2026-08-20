package com.hope.trading.market_intelligence.application.port;

import java.time.Instant;
import java.util.UUID;

public record AnalysisPipelineRunView(
        UUID analysisExecutionId,
        String pipelineVersion,
        String state,
        UUID opportunityId,
        Long opportunityVersion,
        String failureCode,
        String failureMessage,
        Instant completedAt
) {
}

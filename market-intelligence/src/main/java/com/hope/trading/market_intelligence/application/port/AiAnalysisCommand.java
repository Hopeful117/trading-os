package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionPolicy;
import com.hope.trading.market_intelligence.domain.security.ExecutionTrace;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.util.List;
import java.util.UUID;

public record AiAnalysisCommand(
        UUID executionId,
        List<String> capabilities,
        IntelligenceContext authorizedContext,
        MarketThesis thesis,
        AnalysisExecutionPolicy policy,
        ExecutionTrace trace
) {
    public AiAnalysisCommand {
        capabilities = List.copyOf(capabilities);
    }

    public AiAnalysisCommand(
            UUID executionId, List<String> capabilities, IntelligenceContext authorizedContext,
            AnalysisExecutionPolicy policy, ExecutionTrace trace) {
        this(executionId, capabilities, authorizedContext, null, policy, trace);
    }
}

package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionPolicy;
import com.hope.trading.market_intelligence.domain.security.ExecutionTrace;

import java.util.List;
import java.util.UUID;

public record AiAnalysisCommand(
        UUID executionId,
        List<String> capabilities,
        IntelligenceContext authorizedContext,
        AnalysisExecutionPolicy policy,
        ExecutionTrace trace
) {
    public AiAnalysisCommand {
        capabilities = List.copyOf(capabilities);
    }
}

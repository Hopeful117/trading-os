package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.domain.ContextRequirement;

import java.time.Duration;
import java.util.List;

public record AnalysisExecutionPlan(
        List<String> capabilityIds,
        List<ContextRequirement> baselineContext,
        int maximumCapabilities,
        Duration timeout
) {
    public AnalysisExecutionPlan {
        capabilityIds = List.copyOf(capabilityIds);
        baselineContext = List.copyOf(baselineContext);
    }
}

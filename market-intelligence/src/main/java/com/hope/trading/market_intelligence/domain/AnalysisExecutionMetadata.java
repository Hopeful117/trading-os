package com.hope.trading.market_intelligence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisExecutionMetadata(
        UUID analysisId,
        AnalysisExecutionMode mode,
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        Duration timeoutBudget,
        List<CapabilityExecution> capabilityExecutions
) {
    public AnalysisExecutionMetadata {
        capabilityExecutions = List.copyOf(capabilityExecutions);
    }
}

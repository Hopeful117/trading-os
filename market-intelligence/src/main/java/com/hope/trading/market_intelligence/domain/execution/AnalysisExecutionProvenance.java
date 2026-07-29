package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;

import java.util.Objects;
import java.util.UUID;

public record AnalysisExecutionProvenance(
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        String strategyVersion
) {
    public AnalysisExecutionProvenance {
        Objects.requireNonNull(marketId);
        Objects.requireNonNull(mode);
        objective = objective == null ? "" : objective;
        Objects.requireNonNull(strategyVersion);
    }
}

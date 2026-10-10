package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;

import java.util.Objects;
import java.util.UUID;

public record AnalysisExecutionProvenance(
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        String strategyVersion,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId
) {
    public AnalysisExecutionProvenance(
            UUID marketId, AnalysisExecutionMode mode, String objective, String strategyVersion) {
        this(marketId, mode, objective, strategyVersion, null, null, null);
    }

    public AnalysisExecutionProvenance {
        Objects.requireNonNull(marketId);
        Objects.requireNonNull(mode);
        objective = objective == null ? "" : objective;
        Objects.requireNonNull(strategyVersion);
    }
}

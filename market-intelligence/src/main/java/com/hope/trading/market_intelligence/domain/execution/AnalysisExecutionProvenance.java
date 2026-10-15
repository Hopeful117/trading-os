package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AnalysisExecutionProvenance(
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        String strategyVersion,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId,
        Instant evaluationCutoff,
        EvidenceAcquisitionPolicy evidencePolicy
) {
    public AnalysisExecutionProvenance(
            UUID marketId, AnalysisExecutionMode mode, String objective, String strategyVersion) {
        this(marketId, mode, objective, strategyVersion, null, null, null, null,
                EvidenceAcquisitionPolicy.LIVE_CURRENT);
    }

    public AnalysisExecutionProvenance(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            String strategyVersion,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId
    ) {
        this(marketId, mode, objective, strategyVersion, accountId, scanId, scanMarketId,
                null, EvidenceAcquisitionPolicy.LIVE_CURRENT);
    }

    public AnalysisExecutionProvenance {
        Objects.requireNonNull(marketId);
        Objects.requireNonNull(mode);
        objective = objective == null ? "" : objective;
        Objects.requireNonNull(strategyVersion);
        evidencePolicy = evidencePolicy == null
                ? EvidenceAcquisitionPolicy.LIVE_CURRENT : evidencePolicy;
    }
}

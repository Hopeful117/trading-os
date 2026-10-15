package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.time.Instant;

public record IntelligenceAnalysisRequestDto(
        @NotNull UUID marketId,
        @NotNull AnalysisExecutionMode mode,
        @Size(max = 500) String objective,
        Instant evaluationCutoff,
        EvidenceAcquisitionPolicy evidencePolicy
) {
    public IntelligenceAnalysisRequestDto(UUID marketId, AnalysisExecutionMode mode, String objective) {
        this(marketId, mode, objective, null, null);
    }
}

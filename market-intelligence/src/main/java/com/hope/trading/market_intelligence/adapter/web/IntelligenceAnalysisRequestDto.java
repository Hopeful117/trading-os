package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record IntelligenceAnalysisRequestDto(
        @NotNull UUID marketId,
        @NotNull AnalysisExecutionMode mode,
        @Size(max = 500) String objective
) {
}

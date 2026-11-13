package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval;
import com.hope.trading.market_intelligence.domain.research.ResearchObjectiveType;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResearchExecutionRequest(
        UUID scopeId,
        @NotNull @Size(min = 1, max = 2) List<UUID> marketIds,
        @NotBlank @Size(max = 500) String objective,
        @NotBlank @Size(max = 32) String depth,
        ResearchObjectiveType objectiveType,
        OhlcInterval timeframe,
        Duration horizon,
        Instant cutoffStart,
        Instant cutoffEnd,
        String provider,
        String productType
) {
    public ResearchExecutionRequest(
            UUID scopeId, List<UUID> marketIds, String objective, String depth,
            ResearchObjectiveType objectiveType, OhlcInterval timeframe, Duration horizon,
            Instant cutoffStart, Instant cutoffEnd) {
        this(scopeId, marketIds, objective, depth, objectiveType, timeframe, horizon,
                cutoffStart, cutoffEnd, null, null);
    }
    public ResearchExecutionRequest(
            UUID scopeId, List<UUID> marketIds, String objective, String depth) {
        this(scopeId, marketIds, objective, depth, ResearchObjectiveType.COMPARATIVE_MARKET_RESEARCH,
                null, null, null, null, null, null);
    }

    public ResearchExecutionRequest {
        objectiveType = objectiveType == null
                ? ResearchObjectiveType.COMPARATIVE_MARKET_RESEARCH : objectiveType;
    }
}

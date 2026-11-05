package com.hope.trading.market_intelligence.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResearchExecutionResponse(
        UUID researchId,
        ResearchExecutionStatus status,
        List<UUID> marketIds,
        String objective,
        String depth,
        Instant requestedAt,
        Instant updatedAt,
        Instant completedAt,
        String failureCode,
        JsonNode report
) {
    static ResearchExecutionResponse from(ResearchExecution execution) {
        return new ResearchExecutionResponse(
                execution.researchId(), execution.status(), execution.marketIds(), execution.objective(),
                execution.depth(), execution.requestedAt(), execution.updatedAt(), execution.completedAt(),
                execution.failureCode(), execution.report());
    }
}

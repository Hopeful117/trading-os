package com.hope.trading.market_intelligence.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStage;

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
        Object report,
        int attemptCount,
        ResearchExecutionStage currentStage,
        Instant lastProgressAt,
        boolean retrying,
        boolean reportAvailable,
        String failureCategory
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static ResearchExecutionResponse from(ResearchExecution execution) {
        return new ResearchExecutionResponse(
                execution.researchId(), execution.status(), execution.marketIds(), execution.objective(),
                execution.depth(), execution.requestedAt(), execution.updatedAt(), execution.completedAt(),
                execution.failureCode(), reportValue(execution.report()), execution.attemptCount(),
                execution.currentStage(), execution.lastProgressAt(),
                execution.status() == ResearchExecutionStatus.REQUESTED && execution.attemptCount() > 0,
                execution.report() != null, failureCategory(execution.failureCode()));
    }

    private static String failureCategory(String failureCode) {
        if (failureCode == null) return null;
        if ("RESEARCH_EXECUTION_EXPIRED".equals(failureCode)) return "TIMEOUT";
        if (failureCode.contains("AUTH") || failureCode.contains("SCOPE")
                || failureCode.contains("FORBIDDEN")) return "AUTHORIZATION_FAILURE";
        if (failureCode.contains("MARKET") || failureCode.contains("ANALYSIS")) {
            return "UPSTREAM_ANALYSIS_FAILURE";
        }
        return "EXECUTION_FAILURE";
    }

    private static Object reportValue(JsonNode report) {
        return report == null ? null : MAPPER.convertValue(report, Object.class);
    }
}

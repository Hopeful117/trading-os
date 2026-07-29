package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.execution.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisExecutionResponse(
        UUID executionId,
        AnalysisExecutionStatus status,
        AnalysisResultQuality resultQuality,
        Instant requestedAt,
        Instant updatedAt,
        Instant expiresAt,
        Instant completedAt,
        List<String> capabilities,
        RetryMetadata retryMetadata
) {
    static AnalysisExecutionResponse from(AnalysisExecution execution) {
        return new AnalysisExecutionResponse(
                execution.executionId(),
                execution.status(),
                execution.resultQuality().orElse(null),
                execution.requestedAt(),
                execution.updatedAt(),
                execution.expiresAt(),
                execution.completedAt().orElse(null),
                execution.capabilities(),
                execution.retryMetadata()
        );
    }
}

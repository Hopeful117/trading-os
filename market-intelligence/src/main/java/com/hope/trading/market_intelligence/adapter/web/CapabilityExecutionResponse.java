package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;

import java.time.Instant;
import java.util.UUID;

public record CapabilityExecutionResponse(
        UUID executionId,
        UUID analysisExecutionId,
        String capabilityId,
        String capabilityVersion,
        String state,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String failureType,
        String errorCode,
        String message,
        Boolean retryable,
        String skipReason
) {
    static CapabilityExecutionResponse from(CapabilityExecution execution) {
        return new CapabilityExecutionResponse(
                execution.id(),
                execution.analysisExecutionId(),
                execution.capabilityId().value(),
                execution.capabilityVersion().value(),
                execution.state().name(),
                execution.createdAt(),
                execution.startedAt().orElse(null),
                execution.completedAt().orElse(null),
                execution.failure().map(value -> value.failureType()).orElse(null),
                execution.failure().map(value -> value.errorCode()).orElse(null),
                execution.failure().map(value -> value.message()).orElse(null),
                execution.failure().map(value -> value.retryable()).orElse(null),
                execution.skipReason().map(Enum::name).orElse(null));
    }
}

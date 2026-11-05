package com.hope.trading.market_intelligence.adapter.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record AiEngineAcceptedResponse(UUID executionId, String status, Instant acceptedAt) {
}

record AiEngineProgressResponse(UUID executionId, String status, Instant updatedAt, String errorType) {
}

record AiEngineResultResponse(
        UUID executionId,
        String status,
        String quality,
        InterpretationWire result,
        String errorType,
        String errorMessage,
        String provider,
        String model,
        Instant acceptedAt,
        Instant completedAt
) {
}

record InterpretationWire(
        String marketSummary,
        String primaryScenario,
        List<ArgumentWire> supportingArguments,
        List<ArgumentWire> contradictions,
        List<String> missingInformation,
        List<String> confirmationConditions,
        List<String> invalidationConditions,
        List<String> uncertainty,
        String suggestedDecision
) {
}

record ArgumentWire(String text, List<UUID> evidenceRefs) {
}

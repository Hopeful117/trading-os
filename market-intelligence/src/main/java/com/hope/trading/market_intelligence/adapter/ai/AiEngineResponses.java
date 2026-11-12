package com.hope.trading.market_intelligence.adapter.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.hope.trading.market_intelligence.application.port.AiHypothesisReconciliation;

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
        Instant completedAt,
        AiProvenanceWire provenance
) {
    AiEngineResultResponse(UUID executionId, String status, String quality, InterpretationWire result,
                           String errorType, String errorMessage, String provider, String model,
                           Instant acceptedAt, Instant completedAt) {
        this(executionId, status, quality, result, errorType, errorMessage, provider, model,
                acceptedAt, completedAt, null);
    }
}

record AiProvenanceWire(
        String executionId,
        String provider,
        String model,
        String classification,
        boolean shadowMode,
        Instant invocationAt,
        Instant completionAt,
        String providerCorrelationId,
        boolean fallback,
        String failureReason,
        String contractVersion
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
        String suggestedDecision,
        RequestedAnalysisWire requestedAnalysis,
        List<AiHypothesisReconciliation> reconciliations
) {
    InterpretationWire(
            String marketSummary,
            String primaryScenario,
            List<ArgumentWire> supportingArguments,
            List<ArgumentWire> contradictions,
            List<String> missingInformation,
            List<String> confirmationConditions,
            List<String> invalidationConditions,
            List<String> uncertainty,
            String suggestedDecision,
            RequestedAnalysisWire requestedAnalysis) {
        this(marketSummary, primaryScenario, supportingArguments, contradictions,
                missingInformation, confirmationConditions, invalidationConditions,
                uncertainty, suggestedDecision, requestedAnalysis, List.of());
    }

    InterpretationWire(
            String marketSummary,
            String primaryScenario,
            List<ArgumentWire> supportingArguments,
            List<ArgumentWire> contradictions,
            List<String> missingInformation,
            List<String> confirmationConditions,
            List<String> invalidationConditions,
            List<String> uncertainty,
            String suggestedDecision) {
        this(marketSummary, primaryScenario, supportingArguments, contradictions,
                missingInformation, confirmationConditions, invalidationConditions,
                uncertainty, suggestedDecision, null, List.of());
    }
}

record ArgumentWire(String text, List<UUID> evidenceRefs) {
}

record RequestedAnalysisWire(
        String capabilityId,
        String capabilityVersion,
        UUID marketId,
        String reason,
        String expectedEvidence
) {
}

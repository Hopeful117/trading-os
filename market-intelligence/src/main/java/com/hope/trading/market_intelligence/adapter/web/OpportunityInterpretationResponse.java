package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.ai.OpportunityInterpretationOrchestrationService;

import java.util.UUID;

public record OpportunityInterpretationResponse(
        UUID executionId,
        String status,
        String mode,
        UUID opportunityId,
        long opportunityVersion,
        UUID contextPackId,
        String contextPackSchemaVersion,
        String contextPackFingerprint
) {
    public static OpportunityInterpretationResponse from(
            OpportunityInterpretationOrchestrationService.OpportunityInterpretationResult result) {
        return new OpportunityInterpretationResponse(
                result.execution().executionId(),
                result.execution().status().name(),
                result.mode().name(),
                result.contextPack().opportunity().opportunityId(),
                result.contextPack().opportunity().version(),
                result.contextPack().id().value(),
                result.contextPack().schemaVersion(),
                result.contextPack().fingerprint().value());
    }
}

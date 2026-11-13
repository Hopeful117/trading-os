package com.hope.trading.market_intelligence.application.port;

import java.util.List;
import java.util.UUID;

public record AiHypothesisProposal(
        String hypothesisId,
        String observedBehavior,
        String proposedRelationship,
        String expectedOutcome,
        List<String> evaluationHorizon,
        List<String> applicabilityConditions,
        List<String> falsificationConditions,
        List<UUID> supportingEvidenceReferences,
        List<String> limitations
) {
    public AiHypothesisProposal {
        evaluationHorizon = evaluationHorizon == null ? List.of() : List.copyOf(evaluationHorizon);
        applicabilityConditions = applicabilityConditions == null ? List.of() : List.copyOf(applicabilityConditions);
        falsificationConditions = falsificationConditions == null ? List.of() : List.copyOf(falsificationConditions);
        supportingEvidenceReferences = supportingEvidenceReferences == null
                ? List.of() : List.copyOf(supportingEvidenceReferences);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}

package com.hope.trading.market_intelligence.domain.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpportunityDiagnosticResult(
        List<DiagnosticReason> reasons,
        List<String> explanation,
        List<UUID> evidenceReferences
) implements AiInterpretationPayload {
    public OpportunityDiagnosticResult {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("At least one diagnostic reason is required");
        }
        explanation = List.copyOf(Objects.requireNonNull(explanation, "explanation"));
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
    }

    @Override
    public AiInterpretationMode mode() {
        return AiInterpretationMode.DIAGNOSTIC;
    }
}

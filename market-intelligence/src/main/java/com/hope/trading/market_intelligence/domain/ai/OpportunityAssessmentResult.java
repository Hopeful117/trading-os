package com.hope.trading.market_intelligence.domain.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpportunityAssessmentResult(
        List<String> findings,
        List<String> uncertainty,
        List<UUID> evidenceReferences
) implements AiInterpretationPayload {
    public OpportunityAssessmentResult {
        findings = copyRequired(findings, "findings");
        uncertainty = List.copyOf(Objects.requireNonNull(uncertainty, "uncertainty"));
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
    }

    @Override
    public AiInterpretationMode mode() {
        return AiInterpretationMode.OPPORTUNITY_ASSESSMENT;
    }

    private static List<String> copyRequired(List<String> values, String name) {
        List<String> copy = List.copyOf(Objects.requireNonNull(values, name));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " cannot contain blank values");
        }
        return copy;
    }
}

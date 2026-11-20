package com.hope.trading.market_intelligence.domain.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DiagnosticReason(
        String code,
        String explanation,
        List<UUID> evidenceReferences
) {
    public DiagnosticReason {
        code = required(code, "code");
        explanation = required(explanation, "explanation");
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}

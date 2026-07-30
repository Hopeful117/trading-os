package com.hope.trading.risk.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TraceMetadata(
        UUID evaluationId, UUID correlationId, String engineVersion,
        Map<String, String> policyVersions, Map<String, String> ruleVersions,
        ContextMetadata context
) {
    public TraceMetadata {
        Objects.requireNonNull(evaluationId); Objects.requireNonNull(correlationId);
        engineVersion = Objects.requireNonNull(engineVersion).trim();
        policyVersions = Map.copyOf(policyVersions);
        ruleVersions = Map.copyOf(ruleVersions);
        Objects.requireNonNull(context);
    }
}

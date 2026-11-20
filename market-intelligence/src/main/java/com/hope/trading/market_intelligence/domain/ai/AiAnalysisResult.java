package com.hope.trading.market_intelligence.domain.ai;

import java.time.Instant;
import java.util.Objects;

public record AiAnalysisResult(
        AiAnalysisIdentity identity,
        AiInterpretationPayload payload,
        Instant acceptedAt,
        AiAnalysisProvenance provenance
) {
    public AiAnalysisResult {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        if (payload.mode() != identity.mode()) {
            throw new IllegalArgumentException("Result payload mode does not match result identity");
        }
        if (provenance != null && provenance.completedAt() != null
                && provenance.completedAt().isAfter(acceptedAt)) {
            throw new IllegalArgumentException("acceptedAt cannot precede provenance completion");
        }
    }
}

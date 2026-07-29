package com.hope.trading.market_intelligence.domain.capability;

import java.time.Instant;
import java.util.*;

public record CapabilityFailure(
        String failureType,
        String errorCode,
        String message,
        boolean retryable,
        ArtifactRequirement failedRequirement,
        UUID producerExecutionId,
        Instant occurredAt,
        Map<String, String> diagnostics
) {
    public CapabilityFailure {
        Objects.requireNonNull(failureType);
        Objects.requireNonNull(errorCode);
        Objects.requireNonNull(message);
        Objects.requireNonNull(occurredAt);
        diagnostics = Map.copyOf(diagnostics);
    }
}

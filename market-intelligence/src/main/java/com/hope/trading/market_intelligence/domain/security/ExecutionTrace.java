package com.hope.trading.market_intelligence.domain.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExecutionTrace(
        UUID executionId,
        String requestId,
        String traceId,
        ServiceIdentity caller,
        AuthorizedCapability operation,
        String contractVersion,
        Instant occurredAt
) {
    public ExecutionTrace {
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(traceId);
        Objects.requireNonNull(caller);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(contractVersion);
        Objects.requireNonNull(occurredAt);
    }
}

package com.hope.trading.market_intelligence.domain.capability;

import java.time.Instant;
import java.util.Objects;

public record CancellationRequest(
        Instant requestedAt, String requestedBy, String reason,
        CancellationSource source, String correlationId) {
    public CancellationRequest {
        Objects.requireNonNull(requestedAt);
        Objects.requireNonNull(requestedBy);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(source);
        Objects.requireNonNull(correlationId);
    }
}

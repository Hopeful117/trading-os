package com.hope.trading.risk.snapshot;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TradingContext(UUID ownerId, UUID accountId, Instant capturedAt,
                             String session, Map<String, String> attributes) {
    public TradingContext {
        Objects.requireNonNull(ownerId); Objects.requireNonNull(accountId);
        Objects.requireNonNull(capturedAt);
        session = Objects.requireNonNull(session).trim();
        attributes = Map.copyOf(attributes);
    }
}

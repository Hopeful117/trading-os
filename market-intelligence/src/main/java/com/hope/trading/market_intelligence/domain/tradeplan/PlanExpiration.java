package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.Objects;

public record PlanExpiration(Instant expiresAt, String policy) {
    public PlanExpiration {
        Objects.requireNonNull(expiresAt);
        policy = Objects.requireNonNull(policy).trim();
        if (policy.isEmpty()) throw new IllegalArgumentException("Expiration policy is required");
    }
}

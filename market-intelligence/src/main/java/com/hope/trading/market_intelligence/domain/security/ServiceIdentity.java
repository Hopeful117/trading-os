package com.hope.trading.market_intelligence.domain.security;

import java.util.Objects;

public record ServiceIdentity(String value) {
    public ServiceIdentity {
        value = Objects.requireNonNull(value, "Service identity is required").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Service identity cannot be empty");
        }
    }

    public static ServiceIdentity marketIntelligence() {
        return new ServiceIdentity("service:market-intelligence");
    }
}

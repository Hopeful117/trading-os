package com.hope.trading.market_intelligence.domain.capability;

import java.util.Objects;

public record CapabilityVersion(String value) {
    public CapabilityVersion {
        value = Objects.requireNonNull(value, "Capability version").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Capability version is required");
    }
}

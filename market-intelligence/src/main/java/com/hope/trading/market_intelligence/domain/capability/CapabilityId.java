package com.hope.trading.market_intelligence.domain.capability;

import java.util.Objects;

public record CapabilityId(String value) {
    public CapabilityId {
        value = required(value, "Capability id");
    }
    private static String required(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}

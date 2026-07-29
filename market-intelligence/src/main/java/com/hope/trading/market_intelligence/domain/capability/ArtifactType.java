package com.hope.trading.market_intelligence.domain.capability;

import java.util.Objects;

public record ArtifactType(String value) {
    public ArtifactType {
        value = Objects.requireNonNull(value, "Artifact type").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Artifact type is required");
    }
}

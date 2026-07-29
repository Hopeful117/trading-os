package com.hope.trading.market_intelligence.domain.capability;

import java.util.Objects;

public record ArtifactVersion(String value) {
    public ArtifactVersion {
        value = Objects.requireNonNull(value, "Artifact version").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Artifact version is required");
    }
}

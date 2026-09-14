package com.hope.trading.trading_core.brokeraccount.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.UUID;

public record RiskProfileReference(
        @NotNull UUID profileId,
        @NotBlank String semanticVersion
) {
    public RiskProfileReference {
        Objects.requireNonNull(profileId, "profileId is required");
        if (semanticVersion == null || semanticVersion.isBlank()) {
            throw new IllegalArgumentException("semanticVersion is required");
        }
        semanticVersion = semanticVersion.strip();
    }
}

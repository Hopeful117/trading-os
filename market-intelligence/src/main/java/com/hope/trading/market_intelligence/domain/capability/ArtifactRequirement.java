package com.hope.trading.market_intelligence.domain.capability;

import java.util.Objects;

public record ArtifactRequirement(
        ArtifactType artifactType,
        ArtifactVersion expectedVersion,
        VersionCompatibilityMode compatibilityMode,
        boolean required,
        ArtifactCardinality cardinality,
        boolean acceptsPartialContext
) {
    public ArtifactRequirement {
        Objects.requireNonNull(artifactType);
        Objects.requireNonNull(expectedVersion);
        Objects.requireNonNull(compatibilityMode);
        Objects.requireNonNull(cardinality);
        if (required && cardinality == ArtifactCardinality.ZERO_OR_MORE) {
            throw new IllegalArgumentException("Required artifact cannot have ZERO_OR_MORE cardinality");
        }
    }
}

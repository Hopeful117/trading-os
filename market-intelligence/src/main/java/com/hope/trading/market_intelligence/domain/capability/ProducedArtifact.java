package com.hope.trading.market_intelligence.domain.capability;

import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;

public record ProducedArtifact(
        ArtifactType type,
        ArtifactVersion version,
        StoredArtifact artifact
) {
    public ProducedArtifact {
        if (artifact.version() != null && !version.equals(artifact.version())) {
            throw new IllegalArgumentException("Produced artifact version does not match stored artifact version");
        }
    }
}

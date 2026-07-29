package com.hope.trading.market_intelligence.domain.capability;

import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;

public record ProducedArtifact(
        ArtifactType type,
        ArtifactVersion version,
        StoredArtifact artifact
) {}

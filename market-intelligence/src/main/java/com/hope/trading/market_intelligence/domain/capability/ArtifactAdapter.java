package com.hope.trading.market_intelligence.domain.capability;

import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;

public interface ArtifactAdapter {
    ArtifactType artifactType();
    ArtifactVersion sourceVersion();
    ArtifactVersion targetVersion();
    StoredArtifact adapt(StoredArtifact source);
}

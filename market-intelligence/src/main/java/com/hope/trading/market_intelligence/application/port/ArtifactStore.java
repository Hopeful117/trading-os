package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.artifact.*;

import java.time.Instant;
import java.util.Optional;

public interface ArtifactStore {
    Optional<StoredArtifact> find(ArtifactCacheKey key);

    StoredArtifact save(StoredArtifact artifact);

    Optional<StoredArtifact> invalidate(ArtifactCacheKey key, Instant at);

    void evict(ArtifactCacheKey key);
}

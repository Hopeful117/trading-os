package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ArtifactStore;
import com.hope.trading.market_intelligence.domain.artifact.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryArtifactStore implements ArtifactStore {
    private final ConcurrentMap<ArtifactCacheKey, StoredArtifact> artifacts =
            new ConcurrentHashMap<>();

    @Override
    public Optional<StoredArtifact> find(ArtifactCacheKey key) {
        return Optional.ofNullable(artifacts.get(key));
    }

    @Override
    public StoredArtifact save(StoredArtifact artifact) {
        artifacts.put(artifact.key(), artifact);
        return artifact;
    }

    @Override
    public Optional<StoredArtifact> invalidate(ArtifactCacheKey key, Instant at) {
        return Optional.ofNullable(artifacts.computeIfPresent(
                key, (ignored, artifact) -> artifact.invalidate(at)
        ));
    }

    @Override
    public void evict(ArtifactCacheKey key) {
        artifacts.remove(key);
    }
}

package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ArtifactDependencyRegistry;
import com.hope.trading.market_intelligence.domain.artifact.*;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.concurrent.*;

@Repository
public class InMemoryArtifactDependencyRegistry implements ArtifactDependencyRegistry {
    private final ConcurrentMap<ArtifactCacheKey, Set<ArtifactCacheKey>> dependents =
            new ConcurrentHashMap<>();

    @Override
    public void register(ArtifactDependency dependency) {
        dependents.computeIfAbsent(
                dependency.source(), ignored -> ConcurrentHashMap.newKeySet()
        ).add(dependency.dependent());
    }

    @Override
    public Set<ArtifactCacheKey> findDependents(ArtifactCacheKey source) {
        return Set.copyOf(dependents.getOrDefault(source, Set.of()));
    }
}

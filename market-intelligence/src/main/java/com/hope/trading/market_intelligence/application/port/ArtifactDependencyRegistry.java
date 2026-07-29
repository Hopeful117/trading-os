package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.artifact.*;

import java.util.Set;

public interface ArtifactDependencyRegistry {
    void register(ArtifactDependency dependency);

    Set<ArtifactCacheKey> findDependents(ArtifactCacheKey source);
}

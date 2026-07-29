package com.hope.trading.market_intelligence.application.artifact;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactCacheKey;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;

/**
 * Performs targeted dependency propagation. Invalidation changes business
 * validity while preserving stored content for audit; eviction remains
 * separate.
 */
@Service
public class ArtifactInvalidationService {
    private final ArtifactStore store;
    private final ArtifactDependencyRegistry dependencies;
    private final Clock clock;

    public ArtifactInvalidationService(
            ArtifactStore store,
            ArtifactDependencyRegistry dependencies,
            Clock clock
    ) {
        this.store = store;
        this.dependencies = dependencies;
        this.clock = clock;
    }

    public Set<ArtifactCacheKey> invalidateWithDependents(ArtifactCacheKey source) {
        Set<ArtifactCacheKey> invalidated = new LinkedHashSet<>();
        Deque<ArtifactCacheKey> pending = new ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty()) {
            ArtifactCacheKey current = pending.removeFirst();
            if (!invalidated.add(current)) {
                continue;
            }
            store.invalidate(current, clock.instant());
            pending.addAll(dependencies.findDependents(current));
        }
        return Set.copyOf(invalidated);
    }
}

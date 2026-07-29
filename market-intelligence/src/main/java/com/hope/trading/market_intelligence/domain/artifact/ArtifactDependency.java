package com.hope.trading.market_intelligence.domain.artifact;

import java.util.Objects;

public record ArtifactDependency(
        ArtifactCacheKey source,
        ArtifactCacheKey dependent
) {
    public ArtifactDependency {
        Objects.requireNonNull(source);
        Objects.requireNonNull(dependent);
        if (source.equals(dependent)) {
            throw new IllegalArgumentException("An artifact cannot depend on itself");
        }
    }
}

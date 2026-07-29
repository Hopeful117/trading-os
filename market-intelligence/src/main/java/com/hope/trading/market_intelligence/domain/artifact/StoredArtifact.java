package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;

import java.time.Instant;
import java.util.Objects;

public record StoredArtifact(
        ArtifactCacheKey key,
        ArtifactContent content,
        ArtifactFreshness freshness,
        ArtifactProvenance provenance,
        AnalysisResultQuality quality
) {
    public StoredArtifact {
        Objects.requireNonNull(key);
        Objects.requireNonNull(content);
        Objects.requireNonNull(freshness);
        Objects.requireNonNull(provenance);
        Objects.requireNonNull(quality);
    }

    public StoredArtifact invalidate(Instant at) {
        return new StoredArtifact(
                key, content, freshness.invalidate(at), provenance, quality
        );
    }
}

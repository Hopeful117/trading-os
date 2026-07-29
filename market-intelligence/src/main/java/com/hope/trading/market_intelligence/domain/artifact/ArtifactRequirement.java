package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;

import java.util.Objects;

public record ArtifactRequirement(
        ArtifactCacheKey key,
        FreshnessPolicy freshnessPolicy,
        AnalysisResultQuality minimumQuality,
        RecalculationPolicy recalculationPolicy,
        boolean required
) {
    public ArtifactRequirement {
        Objects.requireNonNull(key);
        Objects.requireNonNull(freshnessPolicy);
        Objects.requireNonNull(minimumQuality);
        Objects.requireNonNull(recalculationPolicy);
    }

    public boolean accepts(AnalysisResultQuality quality) {
        return qualityRank(quality) >= qualityRank(minimumQuality);
    }

    private int qualityRank(AnalysisResultQuality quality) {
        return switch (quality) {
            case COMPLETE -> 3;
            case PARTIAL -> 2;
            case DEGRADED -> 1;
        };
    }
}

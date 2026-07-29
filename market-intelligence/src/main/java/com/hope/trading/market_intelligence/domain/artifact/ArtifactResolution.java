package com.hope.trading.market_intelligence.domain.artifact;

import java.util.Optional;

public record ArtifactResolution(
        ReuseDecision decision,
        StoredArtifact artifact,
        FreshnessAssessment freshness,
        ArtifactProvenance provenance,
        boolean reused,
        boolean recalculationRequired,
        boolean degraded,
        String reason
) {
    public ArtifactResolution {
        if (decision == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Resolution decision and reason are required");
        }
        if ((decision == ReuseDecision.REUSE
                || decision == ReuseDecision.REUSE_WITH_WARNING)
                && (artifact == null || freshness == null || provenance == null || !reused)) {
            throw new IllegalArgumentException("Reuse resolution requires a traced artifact");
        }
        if (decision == ReuseDecision.RECALCULATE && !recalculationRequired) {
            throw new IllegalArgumentException("Recalculation decision must request recalculation");
        }
    }

    public Optional<StoredArtifact> resolvedArtifact() {
        return Optional.ofNullable(artifact);
    }

    public Optional<ArtifactProvenance> resolvedProvenance() {
        return Optional.ofNullable(provenance);
    }
}

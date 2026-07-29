package com.hope.trading.market_intelligence.domain.artifact;

import java.util.Objects;

/**
 * Technology-independent business key. Infrastructure serializers may derive
 * physical keys from it, but the domain never creates Redis/Caffeine keys.
 */
public record ArtifactCacheKey(
        ArtifactIdentity identity,
        ArtifactScope scope,
        ArtifactFingerprint parametersFingerprint,
        ArtifactFingerprint inputFingerprint
) {
    public ArtifactCacheKey {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(parametersFingerprint);
        Objects.requireNonNull(inputFingerprint);
    }
}

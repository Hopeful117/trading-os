package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.context.ContextClassification;

import java.util.Map;
import java.util.Objects;

public record AiArtifactIdentity(
        String capabilityId,
        String capabilityVersion,
        ArtifactFingerprint contextFingerprint,
        String promptVersion,
        String modelCompatibilityClass,
        ArtifactFingerprint inferencePolicyFingerprint,
        ArtifactFingerprint authorizedToolsFingerprint,
        ContextClassification securityScope
) {
    public AiArtifactIdentity {
        Objects.requireNonNull(capabilityId);
        Objects.requireNonNull(capabilityVersion);
        Objects.requireNonNull(contextFingerprint);
        Objects.requireNonNull(promptVersion);
        Objects.requireNonNull(modelCompatibilityClass);
        Objects.requireNonNull(inferencePolicyFingerprint);
        Objects.requireNonNull(authorizedToolsFingerprint);
        Objects.requireNonNull(securityScope);
    }

    public ArtifactCacheKey toCacheKey(ArtifactScope scope) {
        ArtifactIdentity identity = new ArtifactIdentity(
                "AI_RESULT", capabilityId, capabilityVersion
        );
        ArtifactFingerprint parameters = ArtifactFingerprint.ofParameters(Map.of(
                "promptVersion", promptVersion,
                "modelCompatibilityClass", modelCompatibilityClass,
                "inferencePolicy", inferencePolicyFingerprint.value(),
                "authorizedTools", authorizedToolsFingerprint.value(),
                "securityScope", securityScope.name()
        ));
        return new ArtifactCacheKey(identity, scope, parameters, contextFingerprint);
    }
}

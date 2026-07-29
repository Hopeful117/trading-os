package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity;

import java.util.List;
import java.util.Objects;

public record ArtifactTrace(
        ArtifactIdentity artifact, String parametersFingerprint, String inputFingerprint,
        List<RawMarketDataReference> rawMarketData
) {
    public ArtifactTrace {
        Objects.requireNonNull(artifact, "artifact");
        parametersFingerprint = Objects.requireNonNull(parametersFingerprint, "parametersFingerprint");
        inputFingerprint = Objects.requireNonNull(inputFingerprint, "inputFingerprint");
        rawMarketData = List.copyOf(rawMarketData);
        if (rawMarketData.isEmpty()) {
            throw new IllegalArgumentException("Artifact trace requires raw market data");
        }
    }
}

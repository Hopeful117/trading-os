package com.hope.trading.market_intelligence.domain.observation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CapabilityResultTrace(
        UUID capabilityExecutionId, String capabilityId, String capabilityVersion,
        List<ArtifactTrace> artifacts
) {
    public CapabilityResultTrace {
        Objects.requireNonNull(capabilityExecutionId, "capabilityExecutionId");
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        capabilityVersion = Objects.requireNonNull(capabilityVersion, "capabilityVersion");
        artifacts = List.copyOf(artifacts);
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("Capability result trace requires an artifact");
        }
    }
}

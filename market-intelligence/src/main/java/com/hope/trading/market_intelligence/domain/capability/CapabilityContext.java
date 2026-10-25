package com.hope.trading.market_intelligence.domain.capability;

import com.hope.trading.market_intelligence.domain.artifact.*;

import java.util.*;
import java.time.Instant;

public record CapabilityContext(
        UUID analysisExecutionId,
        UUID capabilityExecutionId,
        Map<ArtifactRequirement, List<StoredArtifact>> resolvedArtifacts,
        Set<ArtifactRequirement> missingRequirements,
        Map<String, Object> parameters,
        List<ArtifactProvenance> provenance,
        CancellationToken cancellationToken,
        Instant evaluationCutoff
) {
    public CapabilityContext {
        resolvedArtifacts = resolvedArtifacts.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())
                )
        );
        missingRequirements = Set.copyOf(missingRequirements);
        parameters = Map.copyOf(parameters);
        provenance = List.copyOf(provenance);
        Objects.requireNonNull(cancellationToken);
    }

    public CapabilityContext(
            UUID analysisExecutionId, UUID capabilityExecutionId,
            Map<ArtifactRequirement, List<StoredArtifact>> resolvedArtifacts,
            Set<ArtifactRequirement> missingRequirements, Map<String, Object> parameters,
            List<ArtifactProvenance> provenance, CancellationToken cancellationToken) {
        this(analysisExecutionId, capabilityExecutionId, resolvedArtifacts, missingRequirements,
                parameters, provenance, cancellationToken, null);
    }
}

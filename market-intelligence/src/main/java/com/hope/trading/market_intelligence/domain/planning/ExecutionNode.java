package com.hope.trading.market_intelligence.domain.planning;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;

public record ExecutionNode(
        UUID id,
        Capability capability,
        CapabilityExecution initialExecution,
        List<ArtifactRequirement> requirements,
        Set<UUID> incomingDependencies,
        Set<UUID> outgoingDependencies,
        List<ProducedContribution.ArtifactContribution> expectedArtifacts
) {
    public ExecutionNode {
        requirements = List.copyOf(requirements);
        incomingDependencies = Set.copyOf(incomingDependencies);
        outgoingDependencies = Set.copyOf(outgoingDependencies);
        expectedArtifacts = List.copyOf(expectedArtifacts);
    }
}

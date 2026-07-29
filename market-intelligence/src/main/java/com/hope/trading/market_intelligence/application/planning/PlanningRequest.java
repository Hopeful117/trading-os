package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.CapabilityId;
import com.hope.trading.market_intelligence.domain.planning.ArtifactDescriptor;

import java.util.*;

public record PlanningRequest(
        UUID analysisExecutionId,
        Set<CapabilityId> explicitlySelected,
        Set<String> satisfiedConditions,
        Set<ArtifactDescriptor> initialArtifacts
) {
    public PlanningRequest {
        explicitlySelected = Set.copyOf(explicitlySelected);
        satisfiedConditions = Set.copyOf(satisfiedConditions);
        initialArtifacts = Set.copyOf(initialArtifacts);
    }
}

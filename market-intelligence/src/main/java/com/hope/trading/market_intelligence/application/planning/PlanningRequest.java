package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.CapabilityId;
import com.hope.trading.market_intelligence.domain.planning.ArtifactDescriptor;

import java.util.*;
import java.time.Instant;

public record PlanningRequest(
        UUID analysisExecutionId,
        Set<CapabilityId> explicitlySelected,
        Set<String> satisfiedConditions,
        Set<ArtifactDescriptor> initialArtifacts,
        String contextFingerprint,
        Instant evaluationCutoff
) {
    public PlanningRequest(
            UUID analysisExecutionId,
            Set<CapabilityId> explicitlySelected,
            Set<String> satisfiedConditions,
            Set<ArtifactDescriptor> initialArtifacts
    ) {
        this(analysisExecutionId, explicitlySelected, satisfiedConditions, initialArtifacts, null);
    }

    public PlanningRequest(
            UUID analysisExecutionId, Set<CapabilityId> explicitlySelected,
            Set<String> satisfiedConditions, Set<ArtifactDescriptor> initialArtifacts,
            String contextFingerprint) {
        this(analysisExecutionId, explicitlySelected, satisfiedConditions, initialArtifacts,
                contextFingerprint, null);
    }

    public PlanningRequest {
        explicitlySelected = Set.copyOf(explicitlySelected);
        satisfiedConditions = Set.copyOf(satisfiedConditions);
        initialArtifacts = Set.copyOf(initialArtifacts);
    }
}

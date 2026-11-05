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
        Instant evaluationCutoff,
        Map<String, Object> parameters
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
                contextFingerprint, null, Map.of());
    }

    public PlanningRequest {
        explicitlySelected = Set.copyOf(explicitlySelected);
        satisfiedConditions = Set.copyOf(satisfiedConditions);
        initialArtifacts = Set.copyOf(initialArtifacts);
        parameters = Map.copyOf(parameters);
    }

    public PlanningRequest(
            UUID analysisExecutionId, Set<CapabilityId> explicitlySelected,
            Set<String> satisfiedConditions, Set<ArtifactDescriptor> initialArtifacts,
            String contextFingerprint, Instant evaluationCutoff) {
        this(analysisExecutionId, explicitlySelected, satisfiedConditions, initialArtifacts,
                contextFingerprint, evaluationCutoff, Map.of());
    }
}

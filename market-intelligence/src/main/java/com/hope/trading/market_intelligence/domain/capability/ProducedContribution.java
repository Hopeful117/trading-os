package com.hope.trading.market_intelligence.domain.capability;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public sealed interface ProducedContribution permits
        ProducedContribution.ArtifactContribution,
        ProducedContribution.ObservationContribution,
        ProducedContribution.MetricContribution,
        ProducedContribution.RecommendationContribution {
    record ArtifactContribution(ArtifactType type, ArtifactVersion version,
                                java.util.Set<ArtifactVersion> backwardCompatibleWith)
            implements ProducedContribution {
        public ArtifactContribution {
            backwardCompatibleWith = java.util.Set.copyOf(backwardCompatibleWith);
        }
        public boolean satisfies(ArtifactRequirement requirement) {
            if (!type.equals(requirement.artifactType())) return false;
            if (version.equals(requirement.expectedVersion())) return true;
            return requirement.compatibilityMode() == VersionCompatibilityMode.BACKWARD_COMPATIBLE
                    && backwardCompatibleWith.contains(requirement.expectedVersion());
        }
    }
    record ObservationContribution(String type) implements ProducedContribution {}
    record MetricContribution(String name) implements ProducedContribution {}
    record RecommendationContribution(String type) implements ProducedContribution {}
}

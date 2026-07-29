package com.hope.trading.market_intelligence.domain.capability;

import java.time.Duration;
import java.util.*;

public record CapabilityMetadata(
        CapabilityId id,
        CapabilityVersion version,
        CapabilityCategory category,
        ExecutionPolicy executionPolicy,
        RetryPolicy retryPolicy,
        List<ArtifactRequirement> requirements,
        List<ProducedContribution> producedContributions,
        Duration timeout,
        String conditionId
) {
    public CapabilityMetadata {
        Objects.requireNonNull(id);
        Objects.requireNonNull(version);
        Objects.requireNonNull(category);
        Objects.requireNonNull(executionPolicy);
        Objects.requireNonNull(retryPolicy);
        requirements = List.copyOf(requirements);
        producedContributions = List.copyOf(producedContributions);
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Capability timeout must be positive");
        }
        if (executionPolicy == ExecutionPolicy.CONDITIONAL
                && (conditionId == null || conditionId.isBlank())) {
            throw new IllegalArgumentException("Conditional capability requires a condition id");
        }
    }
}

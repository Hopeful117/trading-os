package com.hope.trading.market_intelligence.domain.capability;

import java.math.BigDecimal;
import java.util.*;

public record CapabilityResult(
        List<ProducedContribution> contributions,
        List<ProducedArtifact> artifacts,
        Map<String, BigDecimal> metrics,
        List<String> diagnostics,
        CapabilityCompleteness completeness
) {
    public CapabilityResult {
        contributions = List.copyOf(contributions);
        artifacts = List.copyOf(artifacts);
        metrics = Map.copyOf(metrics);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(completeness);
    }
    public static CapabilityResult noOpportunity(List<String> diagnostics) {
        return new CapabilityResult(
                List.of(), List.of(), Map.of(), diagnostics,
                CapabilityCompleteness.COMPLETE
        );
    }
}

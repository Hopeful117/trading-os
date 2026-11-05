package com.hope.trading.market_intelligence.domain.capability;

import com.hope.trading.market_intelligence.domain.IntelligenceFinding;
import java.math.BigDecimal;
import java.util.*;

public record CapabilityResult(
        List<ProducedContribution> contributions,
        List<ProducedArtifact> artifacts,
        Map<String, BigDecimal> metrics,
        List<String> diagnostics,
        CapabilityCompleteness completeness,
        List<IntelligenceFinding> findings
) {
    public CapabilityResult {
        contributions = List.copyOf(contributions);
        artifacts = List.copyOf(artifacts);
        metrics = Map.copyOf(metrics);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(completeness);
        findings = List.copyOf(findings);
    }

    public CapabilityResult(
            List<ProducedContribution> contributions, List<ProducedArtifact> artifacts,
            Map<String, BigDecimal> metrics, List<String> diagnostics,
            CapabilityCompleteness completeness) {
        this(contributions, artifacts, metrics, diagnostics, completeness, List.of());
    }
    public static CapabilityResult noOpportunity(List<String> diagnostics) {
        return noOpportunity(diagnostics, CapabilityCompleteness.COMPLETE);
    }

    public static CapabilityResult noOpportunity(
            List<String> diagnostics, CapabilityCompleteness completeness) {
        return new CapabilityResult(
                List.of(), List.of(), Map.of(), diagnostics,
                completeness, List.of()
        );
    }
}

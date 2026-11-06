package com.hope.trading.market_intelligence.domain;

import java.util.List;
import java.util.UUID;

public record ConsolidatedIntelligence(
        UUID analysisId,
        UUID marketId,
        AnalysisExecutionMode mode,
        IntelligenceExecutionStatus status,
        List<ContextSectionSummary> contextSections,
        List<IntelligenceFinding> findings,
        List<String> warnings,
        AnalysisExecutionMetadata executionMetadata
) {
    public ConsolidatedIntelligence {
        contextSections = immutableOrEmpty(contextSections);
        findings = immutableOrEmpty(findings);
        warnings = immutableOrEmpty(warnings);
    }

    private static <T> List<T> immutableOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

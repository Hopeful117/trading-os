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
        contextSections = List.copyOf(contextSections);
        findings = List.copyOf(findings);
        warnings = List.copyOf(warnings);
    }
}

package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.IntelligenceFinding;
import com.hope.trading.market_intelligence.application.port.RequestedAnalysis;
import com.hope.trading.market_intelligence.application.port.AiHypothesisReconciliation;

import java.util.List;

public record CapabilityAnalysisResult(
        List<IntelligenceFinding> findings,
        List<String> warnings,
        RequestedAnalysis requestedAnalysis,
        List<AiHypothesisReconciliation> reconciliations
) {
    public CapabilityAnalysisResult(List<IntelligenceFinding> findings, List<String> warnings) {
        this(findings, warnings, null, List.of());
    }

    public CapabilityAnalysisResult(List<IntelligenceFinding> findings, List<String> warnings,
                                    RequestedAnalysis requestedAnalysis) {
        this(findings, warnings, requestedAnalysis, List.of());
    }

    public CapabilityAnalysisResult {
        findings = List.copyOf(findings);
        warnings = List.copyOf(warnings);
        reconciliations = List.copyOf(reconciliations);
    }

    public static CapabilityAnalysisResult empty(String warning) {
        return new CapabilityAnalysisResult(List.of(), List.of(warning));
    }
}

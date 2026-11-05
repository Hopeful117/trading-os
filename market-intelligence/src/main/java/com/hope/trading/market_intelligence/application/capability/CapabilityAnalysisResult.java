package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.IntelligenceFinding;
import com.hope.trading.market_intelligence.application.port.RequestedAnalysis;

import java.util.List;

public record CapabilityAnalysisResult(
        List<IntelligenceFinding> findings,
        List<String> warnings,
        RequestedAnalysis requestedAnalysis
) {
    public CapabilityAnalysisResult(List<IntelligenceFinding> findings, List<String> warnings) {
        this(findings, warnings, null);
    }

    public CapabilityAnalysisResult {
        findings = List.copyOf(findings);
        warnings = List.copyOf(warnings);
    }

    public static CapabilityAnalysisResult empty(String warning) {
        return new CapabilityAnalysisResult(List.of(), List.of(warning));
    }
}

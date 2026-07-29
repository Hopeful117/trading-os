package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.IntelligenceFinding;

import java.util.List;

public record CapabilityAnalysisResult(
        List<IntelligenceFinding> findings,
        List<String> warnings
) {
    public CapabilityAnalysisResult {
        findings = List.copyOf(findings);
        warnings = List.copyOf(warnings);
    }

    public static CapabilityAnalysisResult empty(String warning) {
        return new CapabilityAnalysisResult(List.of(), List.of(warning));
    }
}

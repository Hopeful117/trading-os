package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.IntelligenceFinding;
import com.hope.trading.market_intelligence.application.port.RequestedAnalysis;
import com.hope.trading.market_intelligence.application.port.AiHypothesisReconciliation;
import com.hope.trading.market_intelligence.application.port.AiProvenance;

import java.util.List;
import com.hope.trading.market_intelligence.application.port.AiHypothesisProposal;

public record CapabilityAnalysisResult(
        List<IntelligenceFinding> findings,
        List<String> warnings,
        RequestedAnalysis requestedAnalysis,
        List<AiHypothesisReconciliation> reconciliations,
        AiProvenance provenance,
        List<AiHypothesisProposal> hypothesisProposals
) {
    public CapabilityAnalysisResult(List<IntelligenceFinding> findings, List<String> warnings) {
        this(findings, warnings, null, List.of(), null, List.of());
    }

    public CapabilityAnalysisResult(List<IntelligenceFinding> findings, List<String> warnings,
                                    RequestedAnalysis requestedAnalysis) {
        this(findings, warnings, requestedAnalysis, List.of(), null, List.of());
    }

    public CapabilityAnalysisResult(List<IntelligenceFinding> findings, List<String> warnings,
                                    RequestedAnalysis requestedAnalysis,
                                    List<AiHypothesisReconciliation> reconciliations) {
        this(findings, warnings, requestedAnalysis, reconciliations, null, List.of());
    }

    public CapabilityAnalysisResult {
        findings = List.copyOf(findings);
        warnings = List.copyOf(warnings);
        reconciliations = List.copyOf(reconciliations);
        hypothesisProposals = hypothesisProposals == null ? List.of() : List.copyOf(hypothesisProposals);
    }

    public static CapabilityAnalysisResult empty(String warning) {
        return new CapabilityAnalysisResult(List.of(), List.of(warning));
    }
}

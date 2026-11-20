package com.hope.trading.market_intelligence.domain.ai;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Deterministic eligibility decision supplied by Market Intelligence before AI submission. */
public record AiAnalysisEligibility(
        OpportunityVersionReference opportunity,
        boolean exactOpportunityVersionExists,
        boolean structurallyCoherent,
        boolean assessmentEligible,
        List<DiagnosticReason> deterministicReasons,
        Set<UUID> authorizedEvidenceReferences
) {
    public AiAnalysisEligibility {
        Objects.requireNonNull(opportunity, "opportunity");
        deterministicReasons = List.copyOf(Objects.requireNonNull(deterministicReasons, "deterministicReasons"));
        authorizedEvidenceReferences = Set.copyOf(
                Objects.requireNonNull(authorizedEvidenceReferences, "authorizedEvidenceReferences"));
        if (assessmentEligible && (!exactOpportunityVersionExists || !structurallyCoherent)) {
            throw new IllegalArgumentException("Assessment eligibility requires an exact coherent opportunity");
        }
    }

    public boolean allows(AiInterpretationMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case OPPORTUNITY_ASSESSMENT -> exactOpportunityVersionExists
                    && structurallyCoherent && assessmentEligible;
            case DIAGNOSTIC -> exactOpportunityVersionExists
                    && structurallyCoherent && !assessmentEligible && !deterministicReasons.isEmpty();
        };
    }
}

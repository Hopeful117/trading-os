package com.hope.trading.market_intelligence.application.research.histdata;

import java.util.List;

public record Ml01DukascopyH1AdmissibilityDecision(
        String profileName,
        String profileVersion,
        DukascopyH1ResearchQualityState state,
        List<Ml01DukascopyH1RejectionReason> rejectionReasons,
        DukascopyH1Bucket bucket
) {
    public Ml01DukascopyH1AdmissibilityDecision {
        if (profileName == null || profileName.isBlank() || profileVersion == null || profileVersion.isBlank()
                || state == null || rejectionReasons == null || bucket == null) {
            throw new IllegalArgumentException("complete Dukascopy admissibility decision is required");
        }
        rejectionReasons = rejectionReasons.stream().distinct().sorted().toList();
        if ((state == DukascopyH1ResearchQualityState.FULL_OBSERVED
                || state == DukascopyH1ResearchQualityState.PARTIAL_EXPLORATORY)
                && !rejectionReasons.isEmpty()) {
            throw new IllegalArgumentException("eligible decisions cannot contain rejection reasons");
        }
    }

    public boolean eligible() {
        return state == DukascopyH1ResearchQualityState.FULL_OBSERVED
                || state == DukascopyH1ResearchQualityState.PARTIAL_EXPLORATORY;
    }
}

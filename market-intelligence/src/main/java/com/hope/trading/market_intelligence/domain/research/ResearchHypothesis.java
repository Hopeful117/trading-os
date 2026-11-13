package com.hope.trading.market_intelligence.domain.research;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResearchHypothesis(
        UUID hypothesisId,
        int version,
        UUID researchId,
        UUID marketId,
        String contextFingerprint,
        ResearchReport.HypothesisProposal proposal,
        String validationStatus,
        Instant createdAt
) {
    public ResearchHypothesis {
        Objects.requireNonNull(hypothesisId, "hypothesisId is required");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(researchId, "researchId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(contextFingerprint, "contextFingerprint is required");
        Objects.requireNonNull(proposal, "proposal is required");
        Objects.requireNonNull(validationStatus, "validationStatus is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}

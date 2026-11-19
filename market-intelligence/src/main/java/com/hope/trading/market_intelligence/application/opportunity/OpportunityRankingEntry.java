package com.hope.trading.market_intelligence.application.opportunity;

import java.util.Objects;

public record OpportunityRankingEntry(
        OpportunityRankingCandidate candidate,
        OpportunityRankingRationale rationale
) {
    public OpportunityRankingEntry {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(rationale, "rationale");
    }
}

package com.hope.trading.market_intelligence.application.opportunity;

import java.util.Comparator;

public interface OpportunityRankingPolicy {
    String version();

    OpportunityRankingEntry evaluate(OpportunityRankingCandidate candidate);

    Comparator<OpportunityRankingEntry> comparator();
}

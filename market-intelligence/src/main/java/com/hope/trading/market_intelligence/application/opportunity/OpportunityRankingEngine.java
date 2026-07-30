package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.util.*;
import java.util.function.Predicate;

public final class OpportunityRankingEngine {
    public List<TradingOpportunity> rank(
            Collection<TradingOpportunity> opportunities,
            OpportunityRankingStrategy strategy,
            Predicate<TradingOpportunity> filter
    ) {
        Comparator<TradingOpportunity> stable = strategy.comparator()
                .thenComparing(item -> item.id().value())
                .thenComparingLong(item -> item.version().value());
        return opportunities.stream().filter(filter).sorted(stable).toList();
    }

    public static OpportunityRankingStrategy highestScoreFirst() {
        return () -> Comparator.comparing(
                TradingOpportunity::score, Comparator.reverseOrder());
    }
}

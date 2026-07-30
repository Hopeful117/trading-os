package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.util.Comparator;

@FunctionalInterface
public interface OpportunityRankingStrategy {
    Comparator<TradingOpportunity> comparator();
}

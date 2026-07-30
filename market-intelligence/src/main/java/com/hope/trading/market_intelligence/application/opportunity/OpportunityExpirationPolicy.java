package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.time.Instant;

@FunctionalInterface
public interface OpportunityExpirationPolicy {
    ExpirationDecision evaluate(TradingOpportunity opportunity, Instant at);
}

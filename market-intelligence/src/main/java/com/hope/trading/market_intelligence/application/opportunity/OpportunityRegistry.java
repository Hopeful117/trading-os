package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.*;

public interface OpportunityRegistry {
    List<TradingOpportunity> active();
    Optional<TradingOpportunity> latest(OpportunityId id);
    List<TradingOpportunity> history(OpportunityId id);
    TradingOpportunity transition(OpportunityId id, OpportunityStatus target);
    List<TradingOpportunity> expireDue(OpportunityExpirationPolicy policy, java.time.Instant at);
}

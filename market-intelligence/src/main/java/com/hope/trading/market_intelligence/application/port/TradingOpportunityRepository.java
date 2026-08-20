package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Instant;
import java.util.*;

public interface TradingOpportunityRepository {
    TradingOpportunity append(TradingOpportunity opportunity);
    Optional<TradingOpportunity> find(OpportunityId id, OpportunityVersion version);
    Optional<TradingOpportunity> findLatest(OpportunityId id);
    List<TradingOpportunity> findActive();
    List<TradingOpportunity> findHistory(OpportunityId id);
    List<TradingOpportunity> findEquivalentCandidates(
            String instrument, OpportunityDirection direction, String scenario,
            String timeframe, Instant evaluatedAfter);
    List<TradingOpportunity> findAllLatest();
    List<TradingOpportunity> findAllExact(Collection<TradingOpportunityVersionRef> refs);
}

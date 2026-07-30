package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

public sealed interface OpportunityCreationResult permits
        OpportunityCreationResult.Created, OpportunityCreationResult.VersionCreated {
    TradingOpportunity opportunity();

    record Created(TradingOpportunity opportunity) implements OpportunityCreationResult {}
    record VersionCreated(TradingOpportunity opportunity) implements OpportunityCreationResult {}
}

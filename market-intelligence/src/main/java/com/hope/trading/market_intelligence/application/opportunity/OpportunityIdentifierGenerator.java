package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;

@FunctionalInterface
public interface OpportunityIdentifierGenerator {
    OpportunityId next();
}

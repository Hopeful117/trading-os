package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;

import java.util.Objects;

public record TradingOpportunityVersionRef(
        OpportunityId opportunityId,
        OpportunityVersion opportunityVersion
) {
    public TradingOpportunityVersionRef {
        Objects.requireNonNull(opportunityId);
        Objects.requireNonNull(opportunityVersion);
    }
}

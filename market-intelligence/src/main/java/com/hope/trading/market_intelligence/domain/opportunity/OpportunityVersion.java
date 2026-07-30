package com.hope.trading.market_intelligence.domain.opportunity;

public record OpportunityVersion(long value) {
    public OpportunityVersion {
        if (value < 1) throw new IllegalArgumentException("Opportunity version starts at 1");
    }
    public OpportunityVersion next() { return new OpportunityVersion(value + 1); }
}

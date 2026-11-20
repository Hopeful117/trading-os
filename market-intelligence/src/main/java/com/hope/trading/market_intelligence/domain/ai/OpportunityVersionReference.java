package com.hope.trading.market_intelligence.domain.ai;

import java.util.Objects;
import java.util.UUID;

public record OpportunityVersionReference(UUID opportunityId, long version) {
    public OpportunityVersionReference {
        Objects.requireNonNull(opportunityId, "opportunityId");
        if (version < 1) {
            throw new IllegalArgumentException("Opportunity version starts at 1");
        }
    }
}

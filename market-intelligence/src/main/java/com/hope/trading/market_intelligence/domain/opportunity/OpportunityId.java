package com.hope.trading.market_intelligence.domain.opportunity;

import java.util.Objects;
import java.util.UUID;

public record OpportunityId(UUID value) {
    public OpportunityId { Objects.requireNonNull(value, "value"); }
}

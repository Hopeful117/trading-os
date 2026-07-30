package com.hope.trading.market_intelligence.domain.tradeplan;

import com.hope.trading.market_intelligence.domain.opportunity.*;
import java.util.Objects;

public record OpportunityPlanReference(OpportunityId id, OpportunityVersion version) {
    public OpportunityPlanReference { Objects.requireNonNull(id); Objects.requireNonNull(version); }
}

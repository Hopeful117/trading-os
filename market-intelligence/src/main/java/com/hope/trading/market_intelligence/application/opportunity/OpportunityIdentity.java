package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.Set;

public record OpportunityIdentity(
        String instrument, OpportunityDirection direction, String scenario,
        String timeframe, Set<ObservationReference> observations
) {
    public OpportunityIdentity { observations = Set.copyOf(observations); }
}

package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;

public final class IllegalOpportunityTransitionException extends IllegalStateException {
    public IllegalOpportunityTransitionException(
            OpportunityStatus source, OpportunityStatus target) {
        super("Illegal Opportunity transition: " + source + " -> " + target);
    }
}

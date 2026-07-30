package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;

import java.util.*;

public final class OpportunityLifecyclePolicy {
    private static final Map<OpportunityStatus, Set<OpportunityStatus>> ALLOWED = Map.of(
            OpportunityStatus.DETECTED,
            EnumSet.of(OpportunityStatus.ANALYZED, OpportunityStatus.EXPIRED),
            OpportunityStatus.ANALYZED,
            EnumSet.of(OpportunityStatus.ACTIVE, OpportunityStatus.EXPIRED),
            OpportunityStatus.ACTIVE,
            EnumSet.of(OpportunityStatus.CONSUMED, OpportunityStatus.EXPIRED));

    public void validate(OpportunityStatus source, OpportunityStatus target) {
        if (!ALLOWED.getOrDefault(source, Set.of()).contains(target)) {
            throw new IllegalOpportunityTransitionException(source, target);
        }
    }
}

package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Clock;
import java.util.*;

public final class UserOpportunityService {
    private final UserOpportunityRepository projections;
    private final TradingOpportunityRepository opportunities;
    private final Clock clock;

    public UserOpportunityService(
            UserOpportunityRepository projections,
            TradingOpportunityRepository opportunities, Clock clock) {
        this.projections = projections;
        this.opportunities = opportunities;
        this.clock = clock;
    }

    public UserOpportunity save(
            UUID userId, OpportunityId opportunityId, boolean favorite, boolean hidden,
            boolean notifications, boolean read, Integer customPriority, String notes) {
        if (opportunities.findLatest(opportunityId).isEmpty()) {
            throw new NoSuchElementException("Opportunity not found");
        }
        return projections.save(new UserOpportunity(
                userId, opportunityId, favorite, hidden, notifications, read,
                customPriority, notes, clock.instant()));
    }
}

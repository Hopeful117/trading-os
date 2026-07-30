package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.time.*;

public final class OpportunityDeduplicationPolicy {
    private final Duration equivalenceWindow;

    public OpportunityDeduplicationPolicy(Duration equivalenceWindow) {
        if (equivalenceWindow == null || equivalenceWindow.isNegative()
                || equivalenceWindow.isZero()) {
            throw new IllegalArgumentException("Equivalence window must be positive");
        }
        this.equivalenceWindow = equivalenceWindow;
    }

    public Duration equivalenceWindow() { return equivalenceWindow; }

    public boolean equivalent(
            OpportunityIdentity requested, TradingOpportunity existing, Instant evaluatedAt
    ) {
        boolean inWindow = !existing.evaluatedAt().isBefore(
                evaluatedAt.minus(equivalenceWindow))
                && !existing.evaluatedAt().isAfter(evaluatedAt.plus(equivalenceWindow));
        return inWindow
                && existing.instrument().equalsIgnoreCase(requested.instrument())
                && existing.direction() == requested.direction()
                && existing.scenario().equalsIgnoreCase(requested.scenario())
                && existing.timeframe().equalsIgnoreCase(requested.timeframe())
                && existing.observations().equals(requested.observations());
    }
}

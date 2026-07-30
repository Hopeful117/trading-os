package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.time.Instant;

public final class ValidityWindowExpirationPolicy implements OpportunityExpirationPolicy {
    @Override public ExpirationDecision evaluate(TradingOpportunity opportunity, Instant at) {
        return opportunity.validUntil().filter(until -> !at.isBefore(until))
                .map(ignored -> ExpirationDecision.expire(
                        ExpirationReason.VALIDITY_WINDOW_EXCEEDED))
                .orElseGet(ExpirationDecision::keep);
    }
}

package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;

import java.math.BigDecimal;

final class PlanningPriceSelector {
    Selection select(OpportunityDirection direction, BigDecimal bid, BigDecimal ask) {
        String side = direction == OpportunityDirection.LONG ? "ASK" : "BID";
        BigDecimal price = direction == OpportunityDirection.LONG ? ask : bid;
        if (direction == OpportunityDirection.NEUTRAL || price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Executable planning price side is unavailable");
        }
        return new Selection(side, price);
    }

    record Selection(String side, BigDecimal price) { }
}

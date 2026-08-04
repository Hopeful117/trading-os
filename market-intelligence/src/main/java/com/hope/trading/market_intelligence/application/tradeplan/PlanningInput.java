package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlanningInput(
        List<TradingOpportunity> opportunities, TradePlanningContext context,
        BigDecimal marketPrice, Instant plannedAt
) {
    public PlanningInput { opportunities = List.copyOf(opportunities); }
    public TradeDirection direction() {
        return opportunities.getFirst().direction()
                == com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection.LONG
                ? TradeDirection.LONG : TradeDirection.SHORT;
    }
    public String instrument() { return opportunities.getFirst().instrument(); }
    public PlanningPreferences preferences() { return context.preferences(); }
}

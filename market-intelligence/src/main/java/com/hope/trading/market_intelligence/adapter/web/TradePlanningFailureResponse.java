package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.PlanningConflict;
import java.util.List;

public record TradePlanningFailureResponse(
        String reason, String explanation, List<PlanningConflict> conflicts
) {
    public TradePlanningFailureResponse { conflicts = List.copyOf(conflicts); }
}

package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import java.util.List;

public sealed interface TradePlanningResult permits
        TradePlanningResult.Success, TradePlanningResult.Failure {
    record Success(TradePlan plan, List<String> warnings) implements TradePlanningResult {
        public Success { warnings = List.copyOf(warnings); }
    }
    record Failure(
            PlanningFailureReason reason, String explanation,
            List<PlanningConflict> conflicts) implements TradePlanningResult {
        public Failure { conflicts = List.copyOf(conflicts); }
    }
}

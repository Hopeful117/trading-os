package com.hope.trading.market_intelligence.adapter.ai;

import com.hope.trading.market_intelligence.application.port.AiTradePlanningPort;
import com.hope.trading.market_intelligence.application.tradeplan.*;
import java.util.List;

public final class DisabledAiTradePlanningAdapter implements AiTradePlanningPort {
    @Override public AiPlanningProposal propose(
            PlanningInput input, List<PlanningContribution> deterministic) {
        return AiPlanningProposal.empty(input);
    }
}

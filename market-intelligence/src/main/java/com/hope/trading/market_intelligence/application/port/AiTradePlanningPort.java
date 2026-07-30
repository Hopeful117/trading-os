package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.application.tradeplan.*;

public interface AiTradePlanningPort {
    AiPlanningProposal propose(PlanningInput input, java.util.List<PlanningContribution> deterministic);
}

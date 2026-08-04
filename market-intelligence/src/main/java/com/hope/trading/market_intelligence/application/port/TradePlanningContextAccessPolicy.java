package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import java.util.UUID;

public interface TradePlanningContextAccessPolicy {
    boolean mayUse(UUID actorId, TradePlanningContext context);
}

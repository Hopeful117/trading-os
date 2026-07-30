package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.*;

public interface TradePlanExecutionBoundary {
    TradePlan loadReadySnapshot(TradePlanId id, TradePlanVersion version);
    TradePlan recordExecuted(TradePlanId id, TradePlanVersion readyVersion);
}

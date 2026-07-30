package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.TradingContext;
import java.util.UUID;

@FunctionalInterface
public interface TradingContextAccessPolicy {
    boolean mayUse(UUID actorId, TradingContext context);
}

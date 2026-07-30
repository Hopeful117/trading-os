package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.TradingContext;
import java.util.*;

public interface TradingContextRepository {
    TradingContext saveSnapshot(TradingContext context);
    Optional<TradingContext> find(UUID id, long version);
    Optional<TradingContext> findLatest(UUID id);
}

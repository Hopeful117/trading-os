package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import java.util.Optional;
import java.util.UUID;

public interface TradePlanningContextRepository {
    void saveSnapshot(TradePlanningContext context);
    Optional<TradePlanningContext> find(UUID id, long version);
    Optional<TradePlanningContext> findLatest(UUID id);
}

package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.util.*;

public interface TradePlanRepository {
    TradePlan append(TradePlan plan);
    Optional<TradePlan> find(TradePlanId id, TradePlanVersion version);
    Optional<TradePlan> findLatest(TradePlanId id);
    default Optional<TradePlan> findLatestForUpdate(TradePlanId id) {
        return findLatest(id);
    }
    Optional<TradePlan> findNext(TradePlanId id, TradePlanVersion version);
    List<TradePlan> history(TradePlanId id);
}

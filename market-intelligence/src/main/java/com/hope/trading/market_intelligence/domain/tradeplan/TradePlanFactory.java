package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;

public final class TradePlanFactory {
    public TradePlan create(
            TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            TradePlanStatus status, TradePlanningContextReference context,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt) {
        return new TradePlan(
                id, version, previous, status, context, execution, rationale, createdAt);
    }
}

package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.UUID;

public final class TradePlanFactory {
    public TradePlan create(
            TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            TradePlanStatus status, TradePlanningContextReference context,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt) {
        return create(id, version, previous, status, context, execution, rationale, createdAt,
                TradePlanOrigin.OPPORTUNITY, null);
    }

    public TradePlan create(
            TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            TradePlanStatus status, TradePlanningContextReference context,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt,
            TradePlanOrigin origin, UUID authorId) {
        return new TradePlan(
                id, version, previous, status, context, execution, rationale, createdAt,
                origin, authorId);
    }
}

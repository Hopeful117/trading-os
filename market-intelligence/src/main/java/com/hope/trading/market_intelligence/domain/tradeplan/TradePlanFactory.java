package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.UUID;

public final class TradePlanFactory {
    public TradePlan create(Values values) {
        return new TradePlan(
                values.id(), values.version(), values.previous(), values.status(), values.context(),
                values.execution(), values.rationale(), values.createdAt(), values.origin(), values.authorId(),
                values.decisionActorId(), values.decisionAt());
    }

    @SuppressWarnings("java:S107")
    public TradePlan create(TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            TradePlanStatus status, TradePlanningContextReference context,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt) {
        return create(new Values(id, version, previous, status, context, execution, rationale,
                createdAt, TradePlanOrigin.OPPORTUNITY, null, null, null));
    }

    @SuppressWarnings("java:S107")
    public TradePlan create(TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            TradePlanStatus status, TradePlanningContextReference context,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt,
            TradePlanOrigin origin, UUID authorId) {
        return create(new Values(id, version, previous, status, context, execution, rationale,
                createdAt, origin, authorId, null, null));
    }

    public record Values(TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            TradePlanStatus status, TradePlanningContextReference context,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt,
            TradePlanOrigin origin, UUID authorId, UUID decisionActorId, Instant decisionAt) {
        public Values(TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
                      TradePlanStatus status, TradePlanningContextReference context,
                      ExecutionParameters execution, TradingRationale rationale, Instant createdAt,
                      TradePlanOrigin origin, UUID authorId) {
            this(id, version, previous, status, context, execution, rationale, createdAt,
                    origin, authorId, null, null);
        }
    }
}

package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;

public sealed interface TradePlanEvent permits
        TradePlanEvent.Created, TradePlanEvent.VersionCreated,
        TradePlanEvent.Accepted, TradePlanEvent.Rejected, TradePlanEvent.Expired,
        TradePlanEvent.ReadyForRiskValidation {
    TradePlanId planId();
    TradePlanVersion version();
    Instant occurredAt();
    record Created(TradePlanId planId, TradePlanVersion version, Instant occurredAt)
            implements TradePlanEvent {}
    record VersionCreated(TradePlanId planId, TradePlanVersion version, Instant occurredAt)
            implements TradePlanEvent {}
    record Accepted(TradePlanId planId, TradePlanVersion version, Instant occurredAt)
            implements TradePlanEvent {}
    record Rejected(TradePlanId planId, TradePlanVersion version, Instant occurredAt)
            implements TradePlanEvent {}
    record Expired(TradePlanId planId, TradePlanVersion version, Instant occurredAt)
            implements TradePlanEvent {}
    record ReadyForRiskValidation(
            TradePlanId planId, TradePlanVersion version, Instant occurredAt)
            implements TradePlanEvent {}
}

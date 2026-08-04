package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.tradeplan.*;

final class TradePlanMapper {
    private final TradePlanFactory factory = new TradePlanFactory();
    TradePlanEntity toEntity(TradePlan plan) {
        return new TradePlanEntity(
                plan.id().value(), plan.version().value(),
                plan.previousVersion().map(TradePlanVersion::value).orElse(null),
                plan.status().name(), plan.planningContext().id(),
                plan.planningContext().version(), plan.planningContext().capturedAt(),
                plan.execution(), plan.rationale(), plan.createdAt());
    }
    TradePlan toDomain(TradePlanEntity entity) {
        return factory.create(
                new TradePlanId(entity.id()), new TradePlanVersion(entity.version()),
                entity.previousVersion() == null ? null
                        : new TradePlanVersion(entity.previousVersion()),
                TradePlanStatus.valueOf(entity.status()),
                new TradePlanningContextReference(
                        entity.contextId(), entity.contextVersion(), entity.contextSnapshotAt()),
                entity.execution(), entity.rationale(), entity.createdAt());
    }
}

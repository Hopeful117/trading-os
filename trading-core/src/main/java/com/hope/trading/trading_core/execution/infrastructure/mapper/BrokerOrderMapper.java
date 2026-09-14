package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.persistence.BrokerFillEntity;
import com.hope.trading.trading_core.execution.infrastructure.persistence.BrokerOrderEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class BrokerOrderMapper {
    public BrokerOrderEntity toEntity(BrokerOrder value, BrokerOrderEntity target) {
        target.id=value.id().value(); target.intentId=value.intentId().value();
        target.attemptId=value.attemptId().value(); target.externalOrderId=value.externalOrderId();
        target.status=value.status().name(); target.createdAt=value.createdAt();
        target.updatedAt=value.updatedAt(); return target;
    }
    public BrokerOrder toDomain(BrokerOrderEntity value) {
        return toDomain(value, List.of());
    }
    public BrokerOrder toDomain(BrokerOrderEntity value, Collection<BrokerFillEntity> fills) {
        return BrokerOrder.rehydrate(new BrokerOrderId(value.id),
                new ExecutionIntentId(value.intentId),new ExecutionAttemptId(value.attemptId),
                value.externalOrderId,BrokerOrderStatus.valueOf(value.status),fills.stream()
                        .map(fill -> new BrokerOrder.Fill(fill.fillId, fill.quantity, fill.price,
                                fill.fee, fill.executedAt)).toList(),
                value.createdAt,value.updatedAt,value.version);
    }
    public BrokerFillEntity toEntity(BrokerOrder.Fill value, UUID brokerOrderId) {
        BrokerFillEntity target = new BrokerFillEntity();
        target.fillId = value.fillId();
        target.brokerOrderId = brokerOrderId;
        target.quantity = value.quantity();
        target.price = value.price();
        target.fee = value.fee();
        target.executedAt = value.executedAt();
        return target;
    }
}

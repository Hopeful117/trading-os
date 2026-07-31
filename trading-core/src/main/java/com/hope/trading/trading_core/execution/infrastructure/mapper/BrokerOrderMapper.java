package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.persistence.BrokerOrderEntity;
import java.util.List;

public final class BrokerOrderMapper {
    public BrokerOrderEntity toEntity(BrokerOrder value, BrokerOrderEntity target) {
        target.id=value.id().value(); target.intentId=value.intentId().value();
        target.attemptId=value.attemptId().value(); target.externalOrderId=value.externalOrderId();
        target.status=value.status().name(); target.createdAt=value.createdAt();
        target.updatedAt=value.updatedAt(); return target;
    }
    public BrokerOrder toDomain(BrokerOrderEntity value) {
        return BrokerOrder.rehydrate(new BrokerOrderId(value.id),
                new ExecutionIntentId(value.intentId),new ExecutionAttemptId(value.attemptId),
                value.externalOrderId,BrokerOrderStatus.valueOf(value.status),List.of(),
                value.createdAt,value.updatedAt,value.version);
    }
}

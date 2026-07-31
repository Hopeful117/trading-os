package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.persistence.ExecutionAttemptEntity;

public final class ExecutionAttemptMapper {
    public ExecutionAttemptEntity toEntity(ExecutionAttempt value, ExecutionAttemptEntity target) {
        target.id=value.id().value(); target.intentId=value.intentId().value();
        target.attemptNumber=value.attemptNumber(); target.status=value.status().name();
        target.brokerCorrelationId=value.brokerCorrelationId(); target.resultCode=value.resultCode();
        target.createdAt=value.createdAt(); target.startedAt=value.startedAt();
        target.completedAt=value.completedAt(); return target;
    }
    public ExecutionAttempt toDomain(ExecutionAttemptEntity value) {
        return ExecutionAttempt.rehydrate(new ExecutionAttemptId(value.id),
                new ExecutionIntentId(value.intentId),value.attemptNumber,
                AttemptStatus.valueOf(value.status),value.brokerCorrelationId,value.resultCode,
                value.createdAt,value.startedAt,value.completedAt,value.version);
    }
}

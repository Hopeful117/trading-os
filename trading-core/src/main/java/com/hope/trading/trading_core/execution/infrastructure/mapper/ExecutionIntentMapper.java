package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.persistence.ExecutionIntentEntity;

public final class ExecutionIntentMapper {
    public ExecutionIntentEntity toEntity(ExecutionIntent value, ExecutionIntentEntity target) {
        target.id=value.id().value(); target.tradePlanId=value.tradePlan().tradePlanId();
        target.tradePlanVersion=value.tradePlan().version();
        target.riskEvaluationId=value.riskApproval().evaluationId();
        target.riskDecision=value.riskApproval().decision().name();
        target.riskApprovedAt=value.riskApproval().approvedAt();
        target.idempotencyKey=value.idempotencyKey().value();
        target.initiatorId=value.initiatorId(); target.brokerAccountId=value.brokerAccountId();
        target.instrument=value.parameters().instrument(); target.side=value.parameters().side().name();
        target.orderType=value.parameters().orderType().name();
        target.quantity=value.parameters().quantity(); target.limitPrice=value.parameters().limitPrice();
        target.status=value.status().name();
        target.activeAttemptId=value.activeAttemptId().map(ExecutionAttemptId::value).orElse(null);
        target.createdAt=value.createdAt(); target.updatedAt=value.updatedAt(); target.expiresAt=value.expiresAt();
        return target;
    }
    public ExecutionIntent toDomain(ExecutionIntentEntity value) {
        return ExecutionIntent.rehydrate(new ExecutionIntentId(value.id),
                new TradePlanReference(value.tradePlanId,value.tradePlanVersion),
                new RiskApprovalReference(value.riskEvaluationId,
                    RiskApprovalReference.Decision.valueOf(value.riskDecision),value.riskApprovedAt),
                new IdempotencyKey(value.idempotencyKey),value.initiatorId,value.brokerAccountId,
                new ExecutionParameters(value.instrument,
                    ExecutionParameters.Side.valueOf(value.side),
                    ExecutionParameters.OrderType.valueOf(value.orderType),
                    value.quantity,value.limitPrice),ExecutionStatus.valueOf(value.status),
                value.activeAttemptId==null?null:new ExecutionAttemptId(value.activeAttemptId),
                value.createdAt,value.updatedAt,value.expiresAt,value.version);
    }
}

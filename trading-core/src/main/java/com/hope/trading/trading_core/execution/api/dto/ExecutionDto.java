package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionDto(
        UUID id,UUID tradePlanId,long tradePlanVersion,UUID riskEvaluationId,
        String idempotencyKey,UUID brokerAccountId,ExecutionStatus status,
        Instant createdAt,Instant updatedAt,Instant expiresAt,long version
) {
    public static ExecutionDto from(ExecutionIntent value){
        return new ExecutionDto(value.id().value(),value.tradePlan().tradePlanId(),
                value.tradePlan().version(),value.riskApproval().evaluationId(),
                value.idempotencyKey().value(),value.brokerAccountId(),value.status(),
                value.createdAt(),value.updatedAt(),value.expiresAt(),value.version());
    }
}

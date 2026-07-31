package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionSummaryDto(UUID id,UUID tradePlanId,ExecutionStatus status,
                                  Instant updatedAt){
    public static ExecutionSummaryDto from(ExecutionIntent value){
        return new ExecutionSummaryDto(value.id().value(),value.tradePlan().tradePlanId(),
                value.status(),value.updatedAt());
    }
}

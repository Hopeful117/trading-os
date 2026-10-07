package com.hope.trading.trading_core.execution.application.command;

import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;
import java.time.Instant;
import java.util.UUID;

public record CreateExecutionIntentCommand(
        TradePlanReference tradePlan, RiskApprovalReference riskApproval,
        IdempotencyKey idempotencyKey, UUID initiatorId, UUID brokerAccountId, UUID accountId,
        ExecutionParameters parameters, java.util.List<TradePlanProvenance> provenance, Instant expiresAt
) {
    public CreateExecutionIntentCommand(TradePlanReference tradePlan, RiskApprovalReference riskApproval,
            IdempotencyKey idempotencyKey, UUID initiatorId, UUID brokerAccountId,
            ExecutionParameters parameters, Instant expiresAt) {
        this(tradePlan, riskApproval, idempotencyKey, initiatorId, brokerAccountId,
                initiatorId, parameters, java.util.List.of(), expiresAt);
    }
}

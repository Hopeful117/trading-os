package com.hope.trading.trading_core.execution.application.command;

import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import java.time.Instant;
import java.util.UUID;

public record CreateExecutionIntentCommand(
        TradePlanReference tradePlan, RiskApprovalReference riskApproval,
        IdempotencyKey idempotencyKey, UUID initiatorId, UUID brokerAccountId,
        ExecutionParameters parameters, Instant expiresAt
) {}

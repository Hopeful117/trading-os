package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateExecutionRequest(
        @NotNull UUID tradePlanId, @Positive long tradePlanVersion,
        @NotNull UUID riskEvaluationId, @NotNull RiskApprovalReference.Decision riskDecision,
        @NotNull Instant riskApprovedAt, @NotBlank @Size(max=160) String idempotencyKey,
        @NotNull UUID brokerAccountId, @NotBlank String instrument,
        @NotNull ExecutionParameters.Side side,
        @NotNull ExecutionParameters.OrderType orderType,
        @NotNull @Positive BigDecimal quantity, @Positive BigDecimal limitPrice,
        @NotNull @Future Instant expiresAt
) {}

package com.hope.trading.trading_core.execution.application.command;

import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import java.time.Instant;
import java.util.UUID;

public record ValidateAndCreateCommand(
        UUID initiatorId,
        UUID tradePlanId,
        long tradePlanVersion,
        UUID evaluationId,
        UUID brokerAccountId,
        IdempotencyKey idempotencyKey,
        Instant expiresAt
) {
    public ValidateAndCreateCommand {
        if (initiatorId == null || tradePlanId == null || evaluationId == null
                || brokerAccountId == null || idempotencyKey == null || expiresAt == null) {
            throw new IllegalArgumentException("All command fields are required");
        }
        if (tradePlanVersion < 1) throw new IllegalArgumentException("TradePlan version starts at 1");
    }
}

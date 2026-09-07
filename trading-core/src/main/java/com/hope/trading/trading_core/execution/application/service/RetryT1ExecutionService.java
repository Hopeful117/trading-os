package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionExpiredException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RetryT1ExecutionService {
    private final ExecutionIntentRepositoryPort intents;
    private final ExecuteTradeService execution;
    private final Clock clock;

    public RetryT1ExecutionService(ExecutionIntentRepositoryPort intents,
                                   ExecuteTradeService execution, Clock clock) {
        this.intents = Objects.requireNonNull(intents);
        this.execution = Objects.requireNonNull(execution);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExecutionIntent retry(ExecutionIntentId id) {
        ExecutionIntent intent = intents.findById(id).orElseThrow(
                () -> new InvalidExecutionStateException("Execution intent not found"));

        // D14: Only RISK_REVALIDATION_UNAVAILABLE is retryable via this endpoint
        if (intent.status() != ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE) {
            throw new InvalidExecutionStateException("Intent is not in retry-eligible state (must be RISK_REVALIDATION_UNAVAILABLE)");
        }

        // Check expiration
        Instant now = clock.instant();
        if (!now.isBefore(intent.expiresAt())) {
            throw new ExecutionExpiredException();
        }

        // Delegate to the same mandatory execution capability (which runs fresh T1)
        return execution.execute(id);
    }
}
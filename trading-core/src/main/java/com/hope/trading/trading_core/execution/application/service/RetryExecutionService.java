package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.time.Clock;
import java.util.Objects;

public final class RetryExecutionService {
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionAttemptRepositoryPort attempts;
    private final ExecuteTradeService execution;
    private final ExecutionEventPublisher events;
    private final ExecutionMetrics metrics;
    private final Clock clock;
    public RetryExecutionService(ExecutionIntentRepositoryPort intents,
            ExecutionAttemptRepositoryPort attempts,
            ExecuteTradeService execution, ExecutionEventPublisher events,
            ExecutionMetrics metrics, Clock clock) {
        this.intents = Objects.requireNonNull(intents); this.attempts = Objects.requireNonNull(attempts);
        this.execution = Objects.requireNonNull(execution);
        this.events = Objects.requireNonNull(events); this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
    }
    public ExecutionIntent retry(ExecutionIntentId id) {
        ExecutionIntent intent = intents.findById(id).orElseThrow(
                () -> new InvalidExecutionStateException("Execution intent not found"));
        if (intent.status() == ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN
                || intent.status() == ExecutionStatus.RECONCILIATION_IN_PROGRESS) {
            intent.addEvent(new ExecutionEvent.ExecutionRetryAborted(
                    id, "RECONCILIATION_REQUIRED", clock.instant()));
            events.publish(intent.pullEvents());
            throw new InvalidExecutionStateException("Reconciliation is required before retry");
        }
        if (intent.status() != ExecutionStatus.FAILED
                && intent.status() != ExecutionStatus.VALIDATED) {
            throw new InvalidExecutionStateException("Execution is not retryable");
        }
        if (intent.status() == ExecutionStatus.FAILED) {
            intent.transition(ExecutionStatus.VALIDATED, clock.instant());
            intents.save(intent);
        }
        intent.addEvent(new ExecutionEvent.ExecutionRetryScheduled(
                id, attempts.findLatestByIntentId(id)
                        .map(attempt -> attempt.attemptNumber() + 1)
                        .orElse(1), clock.instant()));
        events.publish(intent.pullEvents()); metrics.retryScheduled();
        return execution.execute(id);
    }
}

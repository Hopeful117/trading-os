package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.command.CreateExecutionIntentCommand;
import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.DuplicateExecutionException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import java.time.Clock;
import java.util.Objects;

public final class CreateExecutionIntentService {
    private final ExecutionIntentRepositoryPort intents;
    private final IdempotencyService idempotency;
    private final ExecutionIdGenerator ids;
    private final ExecutionEventPublisher events;
    private final ExecutionMetrics metrics;
    private final Clock clock;
    public CreateExecutionIntentService(ExecutionIntentRepositoryPort intents,
            IdempotencyService idempotency, ExecutionIdGenerator ids,
            ExecutionEventPublisher events, ExecutionMetrics metrics, Clock clock) {
        this.intents = Objects.requireNonNull(intents); this.idempotency = Objects.requireNonNull(idempotency);
        this.ids = Objects.requireNonNull(ids); this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics); this.clock = Objects.requireNonNull(clock);
    }
    public ExecutionIntent create(CreateExecutionIntentCommand command) {
        try {
            idempotency.ensureUnique(command.idempotencyKey(), intents);
        } catch (DuplicateExecutionException exception) {
            metrics.duplicatePrevented();
            throw exception;
        }
        ExecutionIntent intent = ExecutionIntent.create(ids.nextIntentId(),
                command.tradePlan(), command.riskApproval(), command.idempotencyKey(),
                command.initiatorId(), command.brokerAccountId(), command.parameters(),
                clock.instant(), command.expiresAt());
        intents.save(intent); events.publish(intent.pullEvents()); metrics.executionCreated();
        return intent;
    }
}

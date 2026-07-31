package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.ExecutionIdGenerator;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import java.util.Objects;

public final class ExecutionAttemptCreationStep {
    private final ExecutionAttemptRepositoryPort attempts;
    private final ExecutionIdGenerator ids;
    public ExecutionAttemptCreationStep(ExecutionAttemptRepositoryPort attempts,
                                        ExecutionIdGenerator ids) {
        this.attempts = Objects.requireNonNull(attempts); this.ids = Objects.requireNonNull(ids);
    }
    public void execute(ExecutionPipelineContext context) {
        int number = attempts.findByIntentId(context.intent().id()).size() + 1;
        ExecutionAttempt attempt = ExecutionAttempt.create(
                ids.nextAttemptId(), context.intent().id(), number, context.now());
        attempts.save(attempt);
        context.attempt(attempt);
        context.intent().addEvent(new ExecutionEvent.ExecutionAttemptCreated(
                context.intent().id(), attempt.id(), number, context.now()));
    }
}

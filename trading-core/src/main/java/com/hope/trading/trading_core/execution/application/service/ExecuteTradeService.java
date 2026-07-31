package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.port.ExecutionEventPublisher;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import java.time.Clock;
import java.util.Objects;

public final class ExecuteTradeService {
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionValidationStep validation;
    private final IdempotencyVerificationStep idempotency;
    private final ExecutionAttemptCreationStep attemptCreation;
    private final BrokerSubmissionStep submission;
    private final BrokerResponseProcessingStep responseProcessing;
    private final ExecutionFinalizationStep finalization;
    private final ExecutionEventPublisher events;
    private final Clock clock;
    public ExecuteTradeService(ExecutionIntentRepositoryPort intents,
            ExecutionValidationStep validation, IdempotencyVerificationStep idempotency,
            ExecutionAttemptCreationStep attemptCreation, BrokerSubmissionStep submission,
            BrokerResponseProcessingStep responseProcessing,
            ExecutionFinalizationStep finalization, ExecutionEventPublisher events, Clock clock) {
        this.intents = Objects.requireNonNull(intents); this.validation = Objects.requireNonNull(validation);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.attemptCreation = Objects.requireNonNull(attemptCreation);
        this.submission = Objects.requireNonNull(submission);
        this.responseProcessing = Objects.requireNonNull(responseProcessing);
        this.finalization = Objects.requireNonNull(finalization);
        this.events = Objects.requireNonNull(events); this.clock = Objects.requireNonNull(clock);
    }
    public ExecutionIntent execute(ExecutionIntentId id) {
        ExecutionIntent intent = intents.findById(id).orElseThrow(
                () -> new InvalidExecutionStateException("Execution intent not found"));
        ExecutionPipelineContext context = new ExecutionPipelineContext(intent, clock.instant());
        validation.execute(context);
        idempotency.execute(context);
        attemptCreation.execute(context);
        submission.execute(context);
        responseProcessing.execute(context);
        finalization.execute(context);
        events.publish(intent.pullEvents());
        return intent;
    }
}

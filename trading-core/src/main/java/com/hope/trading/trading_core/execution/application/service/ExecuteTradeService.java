package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.port.ExecutionEventPublisher;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionExpiredException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.time.Clock;
import java.time.Instant;
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
    private final ExecutionTimeRiskRevalidationService t1Revalidation;
    public ExecuteTradeService(ExecutionIntentRepositoryPort intents,
            ExecutionValidationStep validation, IdempotencyVerificationStep idempotency,
            ExecutionAttemptCreationStep attemptCreation, BrokerSubmissionStep submission,
            BrokerResponseProcessingStep responseProcessing,
            ExecutionFinalizationStep finalization, ExecutionEventPublisher events, Clock clock,
            ExecutionTimeRiskRevalidationService t1Revalidation) {
        this.intents = Objects.requireNonNull(intents); this.validation = Objects.requireNonNull(validation);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.attemptCreation = Objects.requireNonNull(attemptCreation);
        this.submission = Objects.requireNonNull(submission);
        this.responseProcessing = Objects.requireNonNull(responseProcessing);
        this.finalization = Objects.requireNonNull(finalization);
        this.events = Objects.requireNonNull(events); this.clock = Objects.requireNonNull(clock);
        this.t1Revalidation = Objects.requireNonNull(t1Revalidation);
    }
    public ExecutionIntent execute(ExecutionIntentId id) {
        ExecutionIntent intent = intents.findById(id).orElseThrow(
                () -> new InvalidExecutionStateException("Execution intent not found"));
        Instant now = clock.instant();

        // Pre-T1 eligibility checks (cheap deterministic preconditions)
        if (intent.status().terminal()) {
            throw new InvalidExecutionStateException("Terminal execution cannot be submitted");
        }
        if (!now.isBefore(intent.expiresAt())) {
            throw new ExecutionExpiredException();
        }

        // Mandatory T1 gate
        var t1Outcome = t1Revalidation.evaluateAndPersist(intent, now);

        // If T1 not approved, return early (T1 service already updated intent status and published events)
        if (!t1Outcome.approved()) {
            intents.save(intent);
            events.publish(intent.pullEvents());
            return intent;
        }

        // T1 APPROVED - continue existing pipeline
        ExecutionPipelineContext context = new ExecutionPipelineContext(intent, now);
        context.t1EvaluationId(t1Outcome.t1EvaluationId());

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

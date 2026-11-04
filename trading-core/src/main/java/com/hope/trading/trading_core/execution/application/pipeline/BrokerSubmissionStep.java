package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.port.T1ApprovalVerifier;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.model.ExecutionPurpose;
import java.util.Objects;

public final class BrokerSubmissionStep {
    private final BrokerExecutionPort broker;
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionAttemptRepositoryPort attempts;
    private final ExecutionLifecycleService lifecycle;
    private final T1ApprovalVerifier t1ApprovalVerifier;
    public BrokerSubmissionStep(BrokerExecutionPort broker,
            ExecutionIntentRepositoryPort intents, ExecutionAttemptRepositoryPort attempts,
            ExecutionLifecycleService lifecycle, T1ApprovalVerifier t1ApprovalVerifier) {
        this.broker = Objects.requireNonNull(broker); this.intents = Objects.requireNonNull(intents);
        this.attempts = Objects.requireNonNull(attempts);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.t1ApprovalVerifier = Objects.requireNonNull(t1ApprovalVerifier);
    }
    public void execute(ExecutionPipelineContext context) {
        if (context.intent().purpose() == ExecutionPurpose.ENTRY
                && (!context.t1Approved()
                || context.t1EvaluationId() == null
                || context.attempt() == null
                || !context.t1EvaluationId().equals(context.attempt().t1EvaluationId())
                || !t1ApprovalVerifier.isApproved(context.t1EvaluationId(), context.intent().id().value()))) {
            throw new InvalidExecutionStateException("T1 risk approval is required before broker submission");
        }
        lifecycle.start(context.intent(), context.attempt(), context.now());
        attempts.save(context.attempt());
        intents.save(context.intent());
        context.submissionResult(broker.submit(new BrokerExecutionPort.ExecutionRequest(
                context.intent().id(), context.attempt().id(),
                context.intent().idempotencyKey(), context.intent().brokerAccountId(),
                context.intent().parameters())));
    }
}

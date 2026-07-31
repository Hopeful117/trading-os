package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import java.util.Objects;

public final class BrokerSubmissionStep {
    private final BrokerExecutionPort broker;
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionAttemptRepositoryPort attempts;
    private final ExecutionLifecycleService lifecycle;
    public BrokerSubmissionStep(BrokerExecutionPort broker,
            ExecutionIntentRepositoryPort intents, ExecutionAttemptRepositoryPort attempts,
            ExecutionLifecycleService lifecycle) {
        this.broker = Objects.requireNonNull(broker); this.intents = Objects.requireNonNull(intents);
        this.attempts = Objects.requireNonNull(attempts);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }
    public void execute(ExecutionPipelineContext context) {
        lifecycle.start(context.intent(), context.attempt(), context.now());
        attempts.save(context.attempt());
        intents.save(context.intent());
        context.submissionResult(broker.submit(new BrokerExecutionPort.ExecutionRequest(
                context.intent().id(), context.attempt().id(),
                context.intent().idempotencyKey(), context.intent().brokerAccountId(),
                context.intent().parameters())));
    }
}

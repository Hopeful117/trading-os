package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import java.util.Objects;

public final class ExecutionFinalizationStep {
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionAttemptRepositoryPort attempts;
    private final BrokerOrderRepositoryPort orders;
    private final ExecutionLifecycleService lifecycle;
    private final ExecutionMetrics metrics;
    public ExecutionFinalizationStep(ExecutionIntentRepositoryPort intents,
            ExecutionAttemptRepositoryPort attempts, BrokerOrderRepositoryPort orders,
            ExecutionLifecycleService lifecycle, ExecutionMetrics metrics) {
        this.intents = Objects.requireNonNull(intents); this.attempts = Objects.requireNonNull(attempts);
        this.orders = Objects.requireNonNull(orders); this.lifecycle = Objects.requireNonNull(lifecycle);
        this.metrics = Objects.requireNonNull(metrics);
    }
    public void execute(ExecutionPipelineContext context) {
        switch (context.submissionResult()) {
            case BrokerExecutionPort.Acknowledged acknowledged -> {
                lifecycle.acknowledged(context.intent(), context.attempt(),
                        context.brokerOrder(), acknowledged.correlationId(), context.now());
                orders.save(context.brokerOrder()); metrics.executionSucceeded();
            }
            case BrokerExecutionPort.Rejected rejected -> {
                lifecycle.rejected(context.intent(), context.attempt(),
                        context.brokerOrder(), rejected.reasonCode(), context.now());
                orders.save(context.brokerOrder()); metrics.executionFailed();
            }
            case BrokerExecutionPort.Unknown ignored -> {
                lifecycle.unknown(context.intent(), context.attempt(), context.now());
                metrics.unknownSubmission();
            }
        }
        attempts.save(context.attempt()); intents.save(context.intent());
    }
}

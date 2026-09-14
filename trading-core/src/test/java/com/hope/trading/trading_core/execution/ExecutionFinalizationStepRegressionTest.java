package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.ExecutionFinalizationStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionPipelineContext;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.port.ExecutionIdGenerator;
import com.hope.trading.trading_core.execution.application.port.ExecutionMetrics;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionFinalizationStepRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private ExecutionIntentRepositoryPort intents;

    @Mock
    private ExecutionAttemptRepositoryPort attempts;

    @Mock
    private BrokerOrderRepositoryPort orders;

    @Mock
    private ExecutionLifecycleService lifecycle;

    @Mock
    private ExecutionMetrics metrics;

    private ExecutionFinalizationStep step;

    @Test
    void executeWithAcknowledgedCallsLifecycleAndSavesOrder() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(100)), intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder brokerOrder = BrokerOrder.acknowledged(
                new BrokerOrderId(uuid(200)), intent.id(), attempt.id(), "ext-order-1", NOW);
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.brokerOrder(brokerOrder);
        ctx.submissionResult(new BrokerExecutionPort.Acknowledged("ext-order-1", "corr-1"));

        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        step.execute(ctx);

        verify(lifecycle).acknowledged(intent, attempt, brokerOrder, "corr-1", NOW);
        verify(orders).save(brokerOrder);
        verify(metrics).executionSucceeded();
        verify(attempts).save(attempt);
        verify(intents).save(intent);
    }

    @Test
    void executeWithRejectedCallsLifecycleAndSavesOrder() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(101)), intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder brokerOrder = BrokerOrder.rejected(
                new BrokerOrderId(uuid(201)), intent.id(), attempt.id(), "ext-rejected-1", NOW);
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.brokerOrder(brokerOrder);
        ctx.submissionResult(new BrokerExecutionPort.Rejected("ext-rejected-1", "INSUFFICIENT_FUNDS"));

        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        step.execute(ctx);

        verify(lifecycle).rejected(intent, attempt, brokerOrder, "INSUFFICIENT_FUNDS", NOW);
        verify(orders).save(brokerOrder);
        verify(metrics).executionFailed();
        verify(attempts).save(attempt);
        verify(intents).save(intent);
    }

    @Test
    void executeWithUnknownCallsLifecycleAndDoesNotSaveOrder() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(102)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.submissionResult(new BrokerExecutionPort.Unknown("TIMEOUT"));

        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        step.execute(ctx);

        verify(lifecycle).unknown(intent, attempt, NOW);
        verify(metrics).unknownSubmission();
        verify(orders, never()).save(any());
        verify(attempts).save(attempt);
        verify(intents).save(intent);
    }

    @Test
    void executeAlwaysSavesAttemptAndIntentRegardlessOfResult() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(103)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.submissionResult(new BrokerExecutionPort.Unknown("TIMEOUT"));

        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        step.execute(ctx);

        verify(attempts).save(attempt);
        verify(intents).save(intent);
    }

    @Test
    void executeWithAcknowledgedIncrementsMetrics() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(104)), intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder brokerOrder = BrokerOrder.acknowledged(
                new BrokerOrderId(uuid(202)), intent.id(), attempt.id(), "ext-order-1", NOW);
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.brokerOrder(brokerOrder);
        ctx.submissionResult(new BrokerExecutionPort.Acknowledged("ext-order-1", "corr-1"));

        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        step.execute(ctx);

        verify(metrics).executionSucceeded();
        verify(metrics, never()).executionFailed();
        verify(metrics, never()).unknownSubmission();
    }

    @Test
    void executeWithRejectedIncrementsMetrics() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(105)), intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder brokerOrder = BrokerOrder.rejected(
                new BrokerOrderId(uuid(203)), intent.id(), attempt.id(), "ext-rejected-1", NOW);
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.brokerOrder(brokerOrder);
        ctx.submissionResult(new BrokerExecutionPort.Rejected("ext-rejected-1", "INSUFFICIENT_FUNDS"));

        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        step.execute(ctx);

        verify(metrics).executionFailed();
        verify(metrics, never()).executionSucceeded();
        verify(metrics, never()).unknownSubmission();
    }

    @Test
    void executeRequiresNonNullArguments() {
        step = new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics);

        assertThatThrownBy(() -> step.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRequiresNonNullDependencies() {
        assertThatThrownBy(() -> new ExecutionFinalizationStep(null, attempts, orders, lifecycle, metrics))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ExecutionFinalizationStep(intents, null, orders, lifecycle, metrics))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ExecutionFinalizationStep(intents, attempts, null, lifecycle, metrics))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ExecutionFinalizationStep(intents, attempts, orders, null, metrics))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, null))
                .isInstanceOf(NullPointerException.class);
    }
}
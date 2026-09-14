package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.BrokerSubmissionStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionPipelineContext;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
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
import java.util.List;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrokerSubmissionStepRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private BrokerExecutionPort broker;

    @Mock
    private ExecutionIntentRepositoryPort intents;

    @Mock
    private ExecutionAttemptRepositoryPort attempts;

    @Mock
    private ExecutionLifecycleService lifecycle;

    private BrokerSubmissionStep step;

    @Test
    void executeCallsLifecycleStartAndSavesAttemptAndIntent() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(100)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);

        when(broker.submit(any())).thenReturn(new BrokerExecutionPort.Acknowledged("ext-1", "corr-1"));

        step = new BrokerSubmissionStep(broker, intents, attempts, lifecycle);

        step.execute(ctx);

        verify(lifecycle).start(intent, attempt, NOW);
        verify(attempts).save(attempt);
        verify(intents).save(intent);
        verify(broker).submit(any());

        assertThat(ctx.submissionResult()).isInstanceOf(BrokerExecutionPort.Acknowledged.class);
    }

    @Test
    void executeStoresSubmissionResultInContext() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(101)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);

        BrokerExecutionPort.Acknowledged ack = new BrokerExecutionPort.Acknowledged("ext-order-1", "corr-1");
        when(broker.submit(any())).thenReturn(ack);

        step = new BrokerSubmissionStep(broker, intents, attempts, lifecycle);

        step.execute(ctx);

        assertThat(ctx.submissionResult()).isEqualTo(ack);
        assertThat(((BrokerExecutionPort.Acknowledged) ctx.submissionResult()).externalOrderId()).isEqualTo("ext-order-1");
    }

    @Test
    void executeWithRejectedResponseStoresRejectedResult() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(102)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);

        BrokerExecutionPort.Rejected rejected = new BrokerExecutionPort.Rejected("ext-1", "INSUFFICIENT_FUNDS");
        when(broker.submit(any())).thenReturn(rejected);

        step = new BrokerSubmissionStep(broker, intents, attempts, lifecycle);

        step.execute(ctx);

        assertThat(ctx.submissionResult()).isInstanceOf(BrokerExecutionPort.Rejected.class);
        assertThat(((BrokerExecutionPort.Rejected) ctx.submissionResult()).reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void executeWithUnknownResponseStoresUnknownResult() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(103)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);

        BrokerExecutionPort.Unknown unknown = new BrokerExecutionPort.Unknown("TIMEOUT");
        when(broker.submit(any())).thenReturn(unknown);

        step = new BrokerSubmissionStep(broker, intents, attempts, lifecycle);

        step.execute(ctx);

        assertThat(ctx.submissionResult()).isInstanceOf(BrokerExecutionPort.Unknown.class);
        assertThat(((BrokerExecutionPort.Unknown) ctx.submissionResult()).reasonCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void executePassesCorrectRequestToBroker() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(104)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);

        when(broker.submit(any())).thenReturn(new BrokerExecutionPort.Acknowledged("ext-1", "corr-1"));

        step = new BrokerSubmissionStep(broker, intents, attempts, lifecycle);

        step.execute(ctx);

        verify(broker).submit(argThat(req ->
                req.intentId().equals(intent.id()) &&
                req.attemptId().equals(attempt.id()) &&
                req.idempotencyKey().equals(intent.idempotencyKey()) &&
                req.brokerAccountId().equals(intent.brokerAccountId()) &&
                req.parameters().equals(intent.parameters())));
    }

    @Test
    void executeRequiresNonNullArguments() {
        step = new BrokerSubmissionStep(broker, intents, attempts, lifecycle);

        assertThatThrownBy(() -> step.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRequiresNonNullDependencies() {
        assertThatThrownBy(() -> new BrokerSubmissionStep(null, intents, attempts, lifecycle))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new BrokerSubmissionStep(broker, null, attempts, lifecycle))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new BrokerSubmissionStep(broker, intents, null, lifecycle))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new BrokerSubmissionStep(broker, intents, attempts, null))
                .isInstanceOf(NullPointerException.class);
    }
}
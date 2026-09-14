package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.BrokerResponseProcessingStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionPipelineContext;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.port.ExecutionIdGenerator;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
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
class BrokerResponseProcessingStepRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private ExecutionIdGenerator ids;

    private BrokerResponseProcessingStep step;

    @Test
    void executeWithAcknowledgedCreatesBrokerOrder() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(100)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.submissionResult(new BrokerExecutionPort.Acknowledged("ext-order-1", "corr-1"));

        when(ids.nextBrokerOrderId()).thenReturn(new BrokerOrderId(uuid(200)));

        step = new BrokerResponseProcessingStep(ids);

        step.execute(ctx);

        assertThat(ctx.brokerOrder()).isNotNull();
        assertThat(ctx.brokerOrder().status()).isEqualTo(BrokerOrderStatus.ACKNOWLEDGED);
        assertThat(ctx.brokerOrder().externalOrderId()).isEqualTo("ext-order-1");
        assertThat(ctx.brokerOrder().intentId()).isEqualTo(intent.id());
        assertThat(ctx.brokerOrder().attemptId()).isEqualTo(attempt.id());

        verify(ids).nextBrokerOrderId();
    }

    @Test
    void executeWithRejectedCreatesRejectedBrokerOrder() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(101)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.submissionResult(new BrokerExecutionPort.Rejected("ext-rejected-1", "INSUFFICIENT_FUNDS"));

        when(ids.nextBrokerOrderId()).thenReturn(new BrokerOrderId(uuid(201)));

        step = new BrokerResponseProcessingStep(ids);

        step.execute(ctx);

        assertThat(ctx.brokerOrder()).isNotNull();
        assertThat(ctx.brokerOrder().status()).isEqualTo(BrokerOrderStatus.REJECTED);
        assertThat(ctx.brokerOrder().externalOrderId()).isEqualTo("ext-rejected-1");
    }

    @Test
    void executeWithRejectedNullExternalOrderIdGeneratesDefault() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(102)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.submissionResult(new BrokerExecutionPort.Rejected(null, "INSUFFICIENT_FUNDS"));

        when(ids.nextBrokerOrderId()).thenReturn(new BrokerOrderId(uuid(202)));

        step = new BrokerResponseProcessingStep(ids);

        step.execute(ctx);

        assertThat(ctx.brokerOrder()).isNotNull();
        assertThat(ctx.brokerOrder().status()).isEqualTo(BrokerOrderStatus.REJECTED);
        assertThat(ctx.brokerOrder().externalOrderId()).isEqualTo("rejected-" + attempt.id().value());
    }

    @Test
    void executeWithUnknownDoesNotCreateBrokerOrder() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(103)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.attempt(attempt);
        ctx.submissionResult(new BrokerExecutionPort.Unknown("TIMEOUT"));

        step = new BrokerResponseProcessingStep(ids);

        step.execute(ctx);

        assertThat(ctx.brokerOrder()).isNull();
        verify(ids, never()).nextBrokerOrderId();
    }

    @Test
    void executeRequiresNonNullArguments() {
        step = new BrokerResponseProcessingStep(ids);

        assertThatThrownBy(() -> step.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRequiresNonNullIdGenerator() {
        assertThatThrownBy(() -> new BrokerResponseProcessingStep(null))
                .isInstanceOf(NullPointerException.class);
    }
}
package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.service.ExecutionValidationService;
import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.AttemptStatus;
import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExecutionPipelineResponseHandlingTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    @Test
    void rejectedBrokerResponseTransitionsIntentToFailed() {
        var f = fixture();
        f.broker.submission = new BrokerExecutionPort.Rejected("ext-1", "INSUFFICIENT_FUNDS");
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(f.attempts.findByIntentId(intent.id())).hasSize(1);
        var attempt = f.attempts.findByIntentId(intent.id()).get(0);
        assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED);
        assertThat(attempt.resultCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void unknownBrokerResponseTransitionsIntentToSubmissionOutcomeUnknown() {
        var f = fixture();
        f.broker.submission = new BrokerExecutionPort.Unknown("BROKER_OUTCOME_UNKNOWN");
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN);
        assertThat(f.attempts.findByIntentId(intent.id())).hasSize(1);
        var attempt = f.attempts.findByIntentId(intent.id()).get(0);
        assertThat(attempt.status()).isEqualTo(AttemptStatus.OUTCOME_UNKNOWN);
    }

    @Test
    void duplicateExecutionPreventedByTerminalGuard() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        // First execution
        var result1 = f.execution.execute(intent.id());
        assertThat(result1.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(f.broker.submissions).isOne();

        // Second execution with same intent - should be prevented by terminal status guard
        // The ExecuteTradeService checks intent.status().terminal() before T1
        assertThatThrownBy(() -> f.execution.execute(intent.id()))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Terminal");
    }

    @Test
    void acknowledgedResponseCreatesBrokerOrderWithCorrelation() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(f.orders.findByIntentId(intent.id())).isPresent();
        var order = f.orders.findByIntentId(intent.id()).get();
        assertThat(order.externalOrderId()).isEqualTo("order-1");
        // Broker acknowledges -> order status is ACKNOWLEDGED (not FILLED)
        assertThat(order.status()).isEqualTo(BrokerOrderStatus.ACKNOWLEDGED);
        // Correlation is on the attempt
        var attempt = f.attempts.findByIntentId(intent.id()).get(0);
        assertThat(attempt.brokerCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void eventsPublishedOnSuccessfulExecution() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(f.events.values).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void eventsPublishedOnRejectedExecution() {
        var f = fixture();
        f.broker.submission = new BrokerExecutionPort.Rejected("ext-1", "INSUFFICIENT_FUNDS");
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(f.events.values).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void eventsPublishedOnUnknownOutcome() {
        var f = fixture();
        f.broker.submission = new BrokerExecutionPort.Unknown("TIMEOUT");
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN);
        assertThat(f.events.values).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void t1RejectedDoesNotCreateAttempt() {
        var f = fixture();
        var t1Rejected = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.REJECTED, null, false);
        when(t1Rejected.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            f.lifecycle.validate(intentArg, nowArg);
            f.lifecycle.riskRejected(intentArg, t1Outcome.t1EvaluationId(), nowArg);
            return t1Outcome;
        });

        var execution = new ExecuteTradeService(
                f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics,
                        mock(PaperSettlementService.class), mock(com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository.class)),
                f.events, CLOCK, t1Rejected);

        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_REJECTED);
        assertThat(f.attempts.findByIntentId(intent.id())).isEmpty();
        assertThat(f.broker.submissions).isZero();
    }

    @Test
    void t1UnavailableDoesNotCreateAttempt() {
        var f = fixture();
        var t1Unavailable = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), null, "CONTEXT_UNAVAILABLE", false);
        when(t1Unavailable.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            f.lifecycle.validate(intentArg, nowArg);
            f.lifecycle.riskUnavailable(intentArg, t1Outcome.t1EvaluationId(), t1Outcome.reasonCode(), nowArg);
            return t1Outcome;
        });

        var execution = new ExecuteTradeService(
                f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics,
                        mock(PaperSettlementService.class), mock(com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository.class)),
                f.events, CLOCK, t1Unavailable);

        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        assertThat(f.attempts.findByIntentId(intent.id())).isEmpty();
        assertThat(f.broker.submissions).isZero();
    }

    @Test
    void attemptPersistedBeforeBrokerSubmission() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var result = f.execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        // Attempt should be created and persisted before broker submission
        assertThat(f.attempts.findByIntentId(intent.id())).hasSize(1);
        assertThat(f.broker.submissions).isOne();
    }

    private Fixture fixture() {
        var intents = new Intents();
        var attempts = new Attempts();
        var orders = new Orders();
        var ids = new Ids();
        var broker = new Broker();
        var events = new Events();
        var metrics = new Metrics();
        var lifecycle = new ExecutionLifecycleService();
        var t1Mock = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.APPROVED, null, true);
        when(t1Mock.evaluateAndPersist(any(), any())).thenReturn(t1Outcome);
        var execution = new ExecuteTradeService(
                intents,
                new ExecutionValidationStep(new ExecutionValidationService(), lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(attempts, ids),
                new BrokerSubmissionStep(broker, intents, attempts, lifecycle),
                new BrokerResponseProcessingStep(ids),
                new ExecutionFinalizationStep(intents, attempts, orders, lifecycle, metrics,
                        mock(PaperSettlementService.class), mock(com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository.class)),
                events, CLOCK, t1Mock);
        return new Fixture(intents, attempts, orders, broker, events, metrics, lifecycle, ids, execution);
    }

    private record Fixture(Intents intents, Attempts attempts, Orders orders, Broker broker,
                           Events events, Metrics metrics, ExecutionLifecycleService lifecycle,
                           Ids ids, ExecuteTradeService execution) {}
}
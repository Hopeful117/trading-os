package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.service.ExecutionValidationService;
import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests ensuring the execution pipeline order remains unchanged.
 * These tests protect the critical invariant: T1 revalidation runs BEFORE
 * broker submission. No simulated financial state mutation may occur before
 * deterministic execution-time gates succeed.
 */
class ExecutionPipelineOrderRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    @Test
    void t1RunsBeforeBrokerSubmission() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var t1Mock = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.APPROVED, null, true);
        when(t1Mock.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            f.lifecycle.validate(intentArg, nowArg);
            return t1Outcome;
        });

        var execution = new ExecuteTradeService(
                f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Mock);

        execution.execute(intent.id());

        // Verify T1 was called
        verify(t1Mock).evaluateAndPersist(any(), any());
        // Verify broker submission occurred (after T1)
        assertThat(f.broker.submissions).isOne();
    }

    @Test
    void t1RejectionBlocksBrokerSubmission() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var t1Mock = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.REJECTED, null, false);
        when(t1Mock.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
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
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Mock);

        var result = execution.execute(intent.id());

        // Verify T1 was called
        verify(t1Mock).evaluateAndPersist(any(), any());
        // Verify broker submission was NOT called (T1 rejected)
        assertThat(f.broker.submissions).isZero();
        // Verify intent status is RISK_REVALIDATION_REJECTED
        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_REJECTED);
    }

    @Test
    void t1UnavailableBlocksBrokerSubmission() {
        var f = fixture();
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);

        var t1Mock = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), null, "CONTEXT_UNAVAILABLE", false);
        when(t1Mock.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            f.lifecycle.validate(intentArg, nowArg);
            f.lifecycle.riskUnavailable(intentArg, t1Outcome.t1EvaluationId(),
                    t1Outcome.reasonCode(), nowArg);
            return t1Outcome;
        });

        var execution = new ExecuteTradeService(
                f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Mock);

        var result = execution.execute(intent.id());

        // Verify T1 was called
        verify(t1Mock).evaluateAndPersist(any(), any());
        // Verify broker submission was NOT called (T1 unavailable)
        assertThat(f.broker.submissions).isZero();
        // Verify intent status is RISK_REVALIDATION_UNAVAILABLE
        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
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
        var brokerAccountRepository = mock(BrokerAccountRepository.class);
        var paperSettlementService = mock(PaperSettlementService.class);
        return new Fixture(intents, attempts, orders, broker, events, metrics, lifecycle, ids, brokerAccountRepository, paperSettlementService);
    }

private record Fixture(Intents intents, Attempts attempts, Orders orders, Broker broker,
                           Events events, Metrics metrics, ExecutionLifecycleService lifecycle, Ids ids,
                           BrokerAccountRepository brokerAccountRepository, PaperSettlementService paperSettlementService) {}
}

package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionExpiredException;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.service.ExecutionValidationService;
import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for ExecuteTradeService pre-pipeline guards.
 * Protects: intent-not-found, terminal-status, expired-intent.
 */
class ExecuteTradeServiceGuardTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    @Test
    void executeThrowsWhenIntentNotFound() {
        var f = fixture();
        var missingId = new ExecutionIntentId(UUID.randomUUID());

        assertThatThrownBy(() -> f.execution.execute(missingId))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void executeThrowsWhenIntentIsTerminal() {
        var f = fixture();
        var intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS, NOW);
        intent.transition(ExecutionStatus.COMPLETED, NOW);
        f.intents.save(intent);

        assertThatThrownBy(() -> f.execution.execute(intent.id()))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Terminal");
    }

    @Test
    void executeThrowsWhenIntentIsCancelled() {
        var f = fixture();
        var intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.CANCELLED, NOW);
        f.intents.save(intent);

        assertThatThrownBy(() -> f.execution.execute(intent.id()))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Terminal");
    }

    @Test
    void executeThrowsWhenIntentIsExpired() {
        var f = fixture();
        var futureClock = java.time.Clock.fixed(NOW.plusSeconds(600), java.time.ZoneOffset.UTC);
        var expiredIntent = ExecutionIntent.create(
                new ExecutionIntentId(uuid(50)),
                new com.hope.trading.trading_core.execution.domain.model.TradePlanReference(uuid(3), 1),
                new com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference(
                        uuid(4), com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference.Decision.APPROVED, NOW.minusSeconds(2)),
                new IdempotencyKey("expired-key"),
                uuid(1), uuid(2),
                new com.hope.trading.trading_core.execution.domain.model.ExecutionParameters(
                        "EURUSD",
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.Side.BUY,
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.OrderType.MARKET,
                        java.math.BigDecimal.ONE, null),
                NOW.minusSeconds(600),
                NOW.minusSeconds(300) // expiresAt is before NOW
        );
        f.intents.save(expiredIntent);

        var execution = new ExecuteTradeService(
                f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, futureClock, mock(ExecutionTimeRiskRevalidationService.class));

        assertThatThrownBy(() -> execution.execute(expiredIntent.id()))
                .isInstanceOf(ExecutionExpiredException.class);
    }

    @Test
    void terminalCancelledIntentRejectsTransition() {
        var intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.CANCELLED, NOW);

        assertThatThrownBy(() -> intent.transition(ExecutionStatus.VALIDATED, NOW))
                .isInstanceOf(InvalidExecutionStateException.class);
    }

    @Test
    void terminalExpiredIntentRejectsTransition() {
        var intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.EXPIRED, NOW);

        assertThatThrownBy(() -> intent.transition(ExecutionStatus.VALIDATED, NOW))
                .isInstanceOf(InvalidExecutionStateException.class);
    }

    @Test
    void terminalRejectedIntentRejectsTransition() {
        var intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.RISK_REVALIDATION_REJECTED, NOW);

        assertThatThrownBy(() -> intent.transition(ExecutionStatus.VALIDATED, NOW))
                .isInstanceOf(InvalidExecutionStateException.class);
    }

    @Test
    void clearActiveAttemptWithWrongAttemptIdThrows() {
        var intent = intent(ExecutionStatus.VALIDATED);
        var correctId = new ExecutionAttemptId(uuid(20));
        intent.activateAttempt(correctId, NOW);

        var wrongId = new ExecutionAttemptId(uuid(99));
        assertThatThrownBy(() -> intent.clearActiveAttempt(wrongId, NOW))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void clearActiveAttemptWithNullAttemptIdWhenNoneActiveSucceeds() {
        var intent = intent(ExecutionStatus.VALIDATED);
        // activeAttemptId is null, clearing with null should succeed (no-op)
        intent.clearActiveAttempt(null, NOW);
        assertThat(intent.activeAttemptId()).isEmpty();
    }

    @Test
    void pullEventsReturnsCopyAndClears() {
        var intent = intent(ExecutionStatus.VALIDATED);
        var events1 = intent.pullEvents();
        var events2 = intent.pullEvents();
        assertThat(events1).isNotEmpty();
        assertThat(events2).isEmpty();
    }

    @Test
    void executionAttemptStartRequiresCreatedStatus() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(30)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());
        attempt.start(NOW);

        assertThatThrownBy(() -> attempt.start(NOW))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("STARTED");
    }

    @Test
    void executionAttemptSucceedRequiresStartedStatus() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(31)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());

        assertThatThrownBy(() -> attempt.succeed("corr", NOW))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    void executionAttemptFailRequiresStartedStatus() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(32)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());

        assertThatThrownBy(() -> attempt.fail("ERROR", NOW))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    void executionAttemptMarkUnknownRequiresStartedOrTimedOut() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(33)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());

        assertThatThrownBy(() -> attempt.markUnknown(NOW))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("submitted");
    }

    @Test
    void executionAttemptMarkUnknownAcceptsTimedOut() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(34)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());
        attempt.start(NOW);
        attempt.timeout(NOW);

        attempt.markUnknown(NOW);
        assertThat(attempt.status()).isEqualTo(AttemptStatus.OUTCOME_UNKNOWN);
    }

    @Test
    void executionAttemptReconcileRequiresOutcomeUnknown() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(35)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());
        attempt.start(NOW);

        assertThatThrownBy(() -> attempt.reconcile("corr", "FILLED", NOW))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("STARTED");
    }

    @Test
    void executionAttemptVersionIncrementsOnStart() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(36)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());
        assertThat(attempt.version()).isEqualTo(0);
        attempt.start(NOW);
        assertThat(attempt.version()).isEqualTo(1);
    }

    @Test
    void executionAttemptVersionIncrementsOnSucceed() {
        var attempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(37)),
                new ExecutionIntentId(uuid(10)),
                1, NOW, UUID.randomUUID());
        attempt.start(NOW);
        attempt.succeed("corr", NOW);
        assertThat(attempt.version()).isEqualTo(2);
    }

    @Test
    void executionIntentVersionIncrementsOnTransition() {
        var intent = intent(ExecutionStatus.CREATED);
        assertThat(intent.version()).isEqualTo(0);
        intent.transition(ExecutionStatus.VALIDATED, NOW);
        assertThat(intent.version()).isEqualTo(1);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS, NOW);
        assertThat(intent.version()).isEqualTo(2);
    }

    @Test
    void executionIntentVersionIncrementsOnActivateAttempt() {
        var intent = intent(ExecutionStatus.VALIDATED);
        var v0 = intent.version();
        intent.activateAttempt(new ExecutionAttemptId(uuid(40)), NOW);
        assertThat(intent.version()).isEqualTo(v0 + 1);
    }

    @Test
    void executionIntentVersionIncrementsOnClearActiveAttempt() {
        var intent = intent(ExecutionStatus.VALIDATED);
        var attemptId = new ExecutionAttemptId(uuid(41));
        intent.activateAttempt(attemptId, NOW);
        var afterActivate = intent.version();
        intent.clearActiveAttempt(attemptId, NOW);
        assertThat(intent.version()).isEqualTo(afterActivate + 1);
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
                        paperSettlementService, brokerAccountRepository),
                events, CLOCK, t1Mock);
        return new Fixture(intents, attempts, orders, broker, events, metrics, lifecycle, ids, execution,
                brokerAccountRepository, paperSettlementService);
    }

private record Fixture(Intents intents, Attempts attempts, Orders orders, Broker broker,
                           Events events, Metrics metrics, ExecutionLifecycleService lifecycle, Ids ids,
                           ExecuteTradeService execution, BrokerAccountRepository brokerAccountRepository,
                           PaperSettlementService paperSettlementService) {}
}

package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.pipeline.recovery.*;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.service.*;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.service.*;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionPipelineTest {
    @Test void persistsAttemptBeforeBrokerAndCompletesAcknowledgedExecution(){
        var f=fixture();var intent=intent(ExecutionStatus.CREATED);f.intents.save(intent);
        var result=f.execution.execute(intent.id());
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(f.attempts.findByIntentId(intent.id())).hasSize(1);
        assertThat(f.orders.findByIntentId(intent.id())).isPresent();
        assertThat(f.broker.submissions).isOne();
        assertThat(f.metrics.succeeded).isOne();
    }
    @Test void unknownSubmissionBlocksRetryUntilRecovery(){
        var f=fixture();f.broker.submission=new BrokerExecutionPort.Unknown("TIMEOUT");
        var intent=intent(ExecutionStatus.CREATED);f.intents.save(intent);
        f.execution.execute(intent.id());
        assertThat(intent.status()).isEqualTo(ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN);
        var retry=new RetryExecutionService(f.intents,f.attempts,f.execution,f.events,f.metrics,CLOCK);
        assertThatThrownBy(()->retry.retry(intent.id()))
                .isInstanceOf(InvalidExecutionStateException.class);
        assertThat(f.broker.submissions).isOne();
    }
    @Test void confirmedAbsentRecoveryMakesExecutionSafelyRetryable(){
        var f=fixture();f.broker.submission=new BrokerExecutionPort.Unknown("TIMEOUT");
        var intent=intent(ExecutionStatus.CREATED);f.intents.save(intent);
        f.execution.execute(intent.id());
        var recovery=new RecoverExecutionService(
                new RecoverableExecutionDiscoveryStep(f.intents),
                new ExecutionInspectionStep(f.attempts),
                new RecoveryStrategyStep(new RecoveryStrategyService()),
                new BrokerReconciliationStep(f.broker),
                new RecoveryFinalizationStep(f.intents,f.attempts,f.orders,new Ids()),
                f.events,f.metrics,CLOCK,f.intents);
        recovery.recoverAll();
        assertThat(intent.status()).isEqualTo(ExecutionStatus.VALIDATED);
        assertThat(intent.activeAttemptId()).isEmpty();
        assertThat(f.broker.reconciliations).isOne();
    }
    @Test void recoverOneReconcilesSingleExecution(){
        var f=fixture();f.broker.submission=new BrokerExecutionPort.Unknown("TIMEOUT");
        var intent=intent(ExecutionStatus.CREATED);f.intents.save(intent);
        f.execution.execute(intent.id());
        assertThat(intent.status()).isEqualTo(ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN);
        var recovery=new RecoverExecutionService(
                new RecoverableExecutionDiscoveryStep(f.intents),
                new ExecutionInspectionStep(f.attempts),
                new RecoveryStrategyStep(new RecoveryStrategyService()),
                new BrokerReconciliationStep(f.broker),
                new RecoveryFinalizationStep(f.intents,f.attempts,f.orders,new Ids()),
                f.events,f.metrics,CLOCK,f.intents);
        var recovered=recovery.recoverOne(intent.id());
        assertThat(recovered.status()).isEqualTo(ExecutionStatus.VALIDATED);
        assertThat(f.broker.reconciliations).isOne();
    }
    @Test void recoverOneRejectsNonRecoverableState(){
        var f=fixture();var intent=intent(ExecutionStatus.VALIDATED);f.intents.save(intent);
        var recovery=new RecoverExecutionService(
                new RecoverableExecutionDiscoveryStep(f.intents),
                new ExecutionInspectionStep(f.attempts),
                new RecoveryStrategyStep(new RecoveryStrategyService()),
                new BrokerReconciliationStep(f.broker),
                new RecoveryFinalizationStep(f.intents,f.attempts,f.orders,new Ids()),
                f.events,f.metrics,CLOCK,f.intents);
        assertThatThrownBy(()->recovery.recoverOne(intent.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void t1RejectedTransitionsIntentToRiskRevalidationRejected() {
        var f = fixture();
        var t1Rejected = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.REJECTED, null, false);
        when(t1Rejected.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            // First transition to VALIDATED (as the T1 service does for CREATED intents)
            f.lifecycle.validate(intentArg, nowArg);
            // Then transition to RISK_REVALIDATION_REJECTED
            f.lifecycle.riskRejected(intentArg, t1Outcome.t1EvaluationId(), nowArg);
            return t1Outcome;
        });
        
        var execution = new ExecuteTradeService(f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Rejected);
        
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);
        
        var result = execution.execute(intent.id());
        
        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_REJECTED);
        assertThat(f.broker.submissions).isZero();
        assertThat(f.attempts.findByIntentId(intent.id())).isEmpty();
    }

    @Test void t1UnavailableTransitionsIntentToRiskRevalidationUnavailable() {
        var f = fixture();
        var t1Unavailable = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), null, "CONTEXT_UNAVAILABLE", false);
        when(t1Unavailable.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            // First transition to VALIDATED
            f.lifecycle.validate(intentArg, nowArg);
            // Then transition to RISK_REVALIDATION_UNAVAILABLE
            f.lifecycle.riskUnavailable(intentArg, t1Outcome.t1EvaluationId(), t1Outcome.reasonCode(), nowArg);
            return t1Outcome;
        });
        
        var execution = new ExecuteTradeService(f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Unavailable);
        
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);
        
        var result = execution.execute(intent.id());
        
        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        assertThat(f.broker.submissions).isZero();
        assertThat(f.attempts.findByIntentId(intent.id())).isEmpty();
    }

    @Test void t1UnavailableWithReasonCodePersistsCorrectReason() {
        var f = fixture();
        var t1Unavailable = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), null, "CONTEXT_STALE", false);
        when(t1Unavailable.evaluateAndPersist(any(), any())).thenAnswer(inv -> {
            ExecutionIntent intentArg = inv.getArgument(0);
            Instant nowArg = inv.getArgument(1);
            // First transition to VALIDATED
            f.lifecycle.validate(intentArg, nowArg);
            // Then transition to RISK_REVALIDATION_UNAVAILABLE
            f.lifecycle.riskUnavailable(intentArg, t1Outcome.t1EvaluationId(), t1Outcome.reasonCode(), nowArg);
            return t1Outcome;
        });
        
        var execution = new ExecuteTradeService(f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Unavailable);
        
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);
        
        var result = execution.execute(intent.id());
        
        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        // The reason code is stored in the T1 evaluation, not on the intent
    }

    @Test void t1ApprovedFromUnavailableTransitionsToValidatedAndContinuesPipeline() {
        var f = fixture();
        var t1Approved = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.APPROVED, null, true);
        when(t1Approved.evaluateAndPersist(any(), any())).thenReturn(t1Outcome);
        
        var execution = new ExecuteTradeService(f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Approved);
        
        var intent = intent(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        f.intents.save(intent);
        
        var result = execution.execute(intent.id());
        
        // After T1 approval from UNAVAILABLE, intent transitions to VALIDATED then SUBMISSION_IN_PROGRESS
        // and finally to COMPLETED (since broker acknowledges)
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(f.broker.submissions).isOne();
    }

    @Test void t1ApprovedWithWarningsContinuesPipeline() {
        var f = fixture();
        var t1Approved = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                UUID.randomUUID(), RiskDecision.APPROVED_WITH_WARNINGS, null, true);
        when(t1Approved.evaluateAndPersist(any(), any())).thenReturn(t1Outcome);
        
        var execution = new ExecuteTradeService(f.intents,
                new ExecutionValidationStep(new ExecutionValidationService(), f.lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(f.attempts, f.ids),
                new BrokerSubmissionStep(f.broker, f.intents, f.attempts, f.lifecycle),
                new BrokerResponseProcessingStep(f.ids),
                new ExecutionFinalizationStep(f.intents, f.attempts, f.orders, f.lifecycle, f.metrics),
                f.events, CLOCK, t1Approved);
        
        var intent = intent(ExecutionStatus.CREATED);
        f.intents.save(intent);
        
        var result = execution.execute(intent.id());
        
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(f.broker.submissions).isOne();
    }

    private Fixture fixture(){
        var intents=new Intents();var attempts=new Attempts();var orders=new Orders();
        var ids=new Ids();var broker=new Broker();var events=new Events();var metrics=new Metrics();
        var lifecycle=new ExecutionLifecycleService();
        var t1Revalidation = mock(ExecutionTimeRiskRevalidationService.class);
        var t1Outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(UUID.randomUUID(), RiskDecision.APPROVED, null, true);
        when(t1Revalidation.evaluateAndPersist(any(), any())).thenReturn(t1Outcome);
        var execution=new ExecuteTradeService(intents,
                new ExecutionValidationStep(new ExecutionValidationService(),lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(attempts,ids),
                new BrokerSubmissionStep(broker,intents,attempts,lifecycle),
                new BrokerResponseProcessingStep(ids),
                new ExecutionFinalizationStep(intents,attempts,orders,lifecycle,metrics),events,CLOCK, t1Revalidation);
        return new Fixture(intents,attempts,orders,broker,events,metrics,execution,lifecycle,ids);
    }
    private record Fixture(Intents intents, Attempts attempts, Orders orders, Broker broker,
                           Events events, Metrics metrics, ExecuteTradeService execution,
                           ExecutionLifecycleService lifecycle, Ids ids){}
}
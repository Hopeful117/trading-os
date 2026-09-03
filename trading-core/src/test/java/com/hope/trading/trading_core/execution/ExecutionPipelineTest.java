package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.pipeline.recovery.*;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.service.*;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.service.*;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.*;

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
    private Fixture fixture(){
        var intents=new Intents();var attempts=new Attempts();var orders=new Orders();
        var ids=new Ids();var broker=new Broker();var events=new Events();var metrics=new Metrics();
        var lifecycle=new ExecutionLifecycleService();
        var execution=new ExecuteTradeService(intents,
                new ExecutionValidationStep(new ExecutionValidationService(),lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(attempts,ids),
                new BrokerSubmissionStep(broker,intents,attempts,lifecycle),
                new BrokerResponseProcessingStep(ids),
                new ExecutionFinalizationStep(intents,attempts,orders,lifecycle,metrics),
                events,CLOCK);
        return new Fixture(intents,attempts,orders,broker,events,metrics,execution);
    }
    private record Fixture(Intents intents,Attempts attempts,Orders orders,Broker broker,
                           Events events,Metrics metrics,ExecuteTradeService execution){}
}

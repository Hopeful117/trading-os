package com.hope.trading.trading_core.execution.infrastructure.configuration;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.execution.application.pipeline.*;
import com.hope.trading.trading_core.execution.application.pipeline.recovery.*;
import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.application.service.*;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class ExecutionConfiguration {
    @Bean IdempotencyService executionIdempotencyService(){return new IdempotencyService();}
    @Bean ExecutionValidationService executionValidationService(){return new ExecutionValidationService();}
    @Bean ExecutionLifecycleService executionLifecycleService(){return new ExecutionLifecycleService();}
    @Bean RecoveryStrategyService recoveryStrategyService(){return new RecoveryStrategyService();}
    @Bean ExecutionIdGenerator executionIdGenerator(){return new ExecutionIdGenerator(){
        public ExecutionIntentId nextIntentId(){return ExecutionIntentId.newId();}
        public ExecutionAttemptId nextAttemptId(){return ExecutionAttemptId.newId();}
        public BrokerOrderId nextBrokerOrderId(){return BrokerOrderId.newId();}
    };}
    @Bean CreateExecutionIntentService createExecutionIntentService(ExecutionIntentRepositoryPort intents,
            IdempotencyService idempotency,ExecutionIdGenerator ids,ExecutionEventPublisher events,
            ExecutionMetrics metrics,Clock clock){
        return new CreateExecutionIntentService(intents,idempotency,ids,events,metrics,clock);
    }
    @Bean ValidateAndCreateService validateAndCreateService(
            RiskPersistence riskPersistence, TradePlanRiskPort tradePlans,
            BrokerAccountRepository brokerAccounts, CreateExecutionIntentService intentCreation,
            ExecutionLifecycleService lifecycle, Clock clock){
        return new ValidateAndCreateService(riskPersistence, tradePlans, brokerAccounts,
                intentCreation, lifecycle, clock);
    }
    @Bean ExecuteTradeService executeTradeService(ExecutionIntentRepositoryPort intents,
            ExecutionAttemptRepositoryPort attempts,BrokerOrderRepositoryPort orders,
            BrokerExecutionPort broker,ExecutionIdGenerator ids,ExecutionEventPublisher events,
            ExecutionMetrics metrics,ExecutionValidationService validation,
            IdempotencyService idempotency,ExecutionLifecycleService lifecycle,Clock clock){
        return new ExecuteTradeService(intents,new ExecutionValidationStep(validation,lifecycle),
                new IdempotencyVerificationStep(idempotency),
                new ExecutionAttemptCreationStep(attempts,ids),
                new BrokerSubmissionStep(broker,intents,attempts,lifecycle),
                new BrokerResponseProcessingStep(ids),
                new ExecutionFinalizationStep(intents,attempts,orders,lifecycle,metrics),events,clock);
    }
    @Bean RetryExecutionService retryExecutionService(ExecutionIntentRepositoryPort intents,
            ExecutionAttemptRepositoryPort attempts,
            ExecuteTradeService execution,ExecutionEventPublisher events,ExecutionMetrics metrics,Clock clock){
        return new RetryExecutionService(intents,attempts,execution,events,metrics,clock);
    }
    @Bean CancelExecutionService cancelExecutionService(ExecutionIntentRepositoryPort intents,
            BrokerOrderRepositoryPort orders,BrokerExecutionPort broker,
            ExecutionEventPublisher events,ExecutionMetrics metrics,Clock clock){
        return new CancelExecutionService(intents,orders,broker,events,metrics,clock);
    }
    @Bean QueryExecutionService queryExecutionService(ExecutionIntentRepositoryPort intents){
        return new QueryExecutionService(intents);
    }
    @Bean RecoverExecutionService recoverExecutionService(ExecutionIntentRepositoryPort intents,
            ExecutionAttemptRepositoryPort attempts,BrokerOrderRepositoryPort orders,
            BrokerExecutionPort broker,ExecutionIdGenerator ids,RecoveryStrategyService strategies,
            ExecutionEventPublisher events,ExecutionMetrics metrics,Clock clock){
        return new RecoverExecutionService(new RecoverableExecutionDiscoveryStep(intents),
                new ExecutionInspectionStep(attempts),new RecoveryStrategyStep(strategies),
                new BrokerReconciliationStep(broker),
                new RecoveryFinalizationStep(intents,attempts,orders,ids),events,metrics,clock,intents);
    }
}

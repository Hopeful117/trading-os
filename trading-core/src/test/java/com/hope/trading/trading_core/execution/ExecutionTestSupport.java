package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

final class ExecutionTestSupport {
    static final Instant NOW=Instant.parse("2026-07-31T10:00:00Z");
    static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    static final UUID USER=uuid(1),ACCOUNT=uuid(2),PLAN=uuid(3),RISK=uuid(4);
    static UUID uuid(long value){return new UUID(0,value);}
    static ExecutionIntent intent(ExecutionStatus status){
        ExecutionIntent value=ExecutionIntent.create(new ExecutionIntentId(uuid(10)),
                new TradePlanReference(PLAN,1),new RiskApprovalReference(RISK,
                    RiskApprovalReference.Decision.APPROVED,NOW.minusSeconds(1)),
                new IdempotencyKey("plan-3-v1"),USER,ACCOUNT,
                new ExecutionParameters("EURUSD",ExecutionParameters.Side.BUY,
                    ExecutionParameters.OrderType.MARKET,BigDecimal.ONE,null),
                NOW.minusSeconds(10),NOW.plusSeconds(600));
        if(status!=ExecutionStatus.CREATED)value.transition(ExecutionStatus.VALIDATED,NOW);
        return value;
    }
    static final class Intents implements ExecutionIntentRepositoryPort{
        final Map<ExecutionIntentId,ExecutionIntent> values=new LinkedHashMap<>();
        public ExecutionIntent save(ExecutionIntent v){values.put(v.id(),v);return v;}
        public Optional<ExecutionIntent> findById(ExecutionIntentId id){return Optional.ofNullable(values.get(id));}
        public Optional<ExecutionIntent> findByIdempotencyKey(IdempotencyKey key){return values.values().stream().filter(v->v.idempotencyKey().equals(key)).findFirst();}
        public List<ExecutionIntent> findByStatuses(Set<ExecutionStatus> statuses){return values.values().stream().filter(v->statuses.contains(v.status())).toList();}
        public List<ExecutionIntent> findByInitiatorId(UUID id){return values.values().stream().filter(v->v.initiatorId().equals(id)).toList();}
        public List<ExecutionIntent> findAll(){return List.copyOf(values.values());}
    }
    static final class Attempts implements ExecutionAttemptRepositoryPort{
        final Map<ExecutionAttemptId,ExecutionAttempt> values=new LinkedHashMap<>();
        public ExecutionAttempt save(ExecutionAttempt v){values.put(v.id(),v);return v;}
        public Optional<ExecutionAttempt> findById(ExecutionAttemptId id){return Optional.ofNullable(values.get(id));}
        public List<ExecutionAttempt> findByIntentId(ExecutionIntentId id){return values.values().stream().filter(v->v.intentId().equals(id)).toList();}
        public Optional<ExecutionAttempt> findLatestByIntentId(ExecutionIntentId id){return findByIntentId(id).stream().max(Comparator.comparingInt(ExecutionAttempt::attemptNumber));}
    }
    static final class Orders implements BrokerOrderRepositoryPort{
        final Map<BrokerOrderId,BrokerOrder> values=new LinkedHashMap<>();
        public BrokerOrder save(BrokerOrder v){values.put(v.id(),v);return v;}
        public Optional<BrokerOrder> findById(BrokerOrderId id){return Optional.ofNullable(values.get(id));}
        public Optional<BrokerOrder> findByIntentId(ExecutionIntentId id){return values.values().stream().filter(v->v.intentId().equals(id)).findFirst();}
    }
    static final class Ids implements ExecutionIdGenerator{
        int attempt=20,order=30,intent=40;
        public ExecutionIntentId nextIntentId(){return new ExecutionIntentId(uuid(intent++));}
        public ExecutionAttemptId nextAttemptId(){return new ExecutionAttemptId(uuid(attempt++));}
        public BrokerOrderId nextBrokerOrderId(){return new BrokerOrderId(uuid(order++));}
    }
    static final class Events implements ExecutionEventPublisher{
        final List<ExecutionEvent> values=new ArrayList<>();
        public void publish(List<ExecutionEvent> events){values.addAll(events);}
    }
    static final class Metrics implements ExecutionMetrics{
        int created,succeeded,failed,cancelled,duplicates,retries,recoveries,unknown;
        public void executionCreated(){created++;} public void executionSucceeded(){succeeded++;}
        public void executionFailed(){failed++;} public void executionCancelled(){cancelled++;}
        public void duplicatePrevented(){duplicates++;} public void retryScheduled(){retries++;}
        public void recoveryStarted(){recoveries++;} public void unknownSubmission(){unknown++;}
    }
    static final class Broker implements BrokerExecutionPort{
        SubmissionResult submission=new Acknowledged("order-1","corr-1");
        ReconciliationResult reconciliation=new ConfirmedAbsent();
        int submissions,reconciliations;
        public SubmissionResult submit(ExecutionRequest request){submissions++;return submission;}
        public void cancel(UUID account,String external){}
        public ReconciliationResult reconcile(ReconciliationRequest request){reconciliations++;return reconciliation;}
    }
}

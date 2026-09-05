package com.hope.trading.broker_service.broker.application.service;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthorizationException;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import java.util.*;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

public final class BrokerOperationServices { private BrokerOperationServices(){}
    private static <T>T require(com.hope.trading.broker_service.broker.domain.provider.BrokerProvider provider,Class<T> type){return provider.capability(type).orElseThrow(()->new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.UnsupportedBrokerProviderException("Provider "+provider.id()+" does not support "+type.getSimpleName()));}
    private static void requireOwnership(com.hope.trading.broker_service.connection.application.BrokerConnectionRepository connections,UUID brokerAccountId,UUID ownerId){
        if(connections.findByBrokerAccountIdAndOwnerId(brokerAccountId,ownerId).isEmpty())
            throw new BrokerAuthorizationException("Broker account is not accessible");
    }
    @Service public static final class GetAccountService {private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;public GetAccountService(BrokerProviderResolver p,BrokerOperationsMetrics m){providers=p;metrics=m;}public AccountSnapshot get(UUID id){return metrics.record("account",()->require(providers.resolve(id),AccountCapability.class).account(id));}}
    @Service public static final class GetPositionsService {private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;public GetPositionsService(BrokerProviderResolver p,BrokerOperationsMetrics m){providers=p;metrics=m;}public List<PositionSnapshot> get(UUID id){return metrics.record("positions",()->require(providers.resolve(id),PositionCapability.class).positions(id));}}
    @Service public static final class GetOrdersService {private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;public GetOrdersService(BrokerProviderResolver p,BrokerOperationsMetrics m){providers=p;metrics=m;}public List<OrderSnapshot> get(UUID id){return metrics.record("orders",()->require(providers.resolve(id),OrderCapability.class).orders(id));}}
    @Service public static final class ExecuteOrderService {private static final Logger log=LoggerFactory.getLogger(ExecuteOrderService.class);private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;private final com.hope.trading.broker_service.connection.application.BrokerConnectionRepository connections;public ExecuteOrderService(BrokerProviderResolver p,BrokerOperationsMetrics m,com.hope.trading.broker_service.connection.application.BrokerConnectionRepository c){providers=p;metrics=m;connections=c;}public ExecutionResult execute(ExecutionRequest r,UUID ownerId){log.info("broker_execution providerAccount={} attempt={}",r.brokerAccountId(),r.executionAttemptId());requireOwnership(connections,r.brokerAccountId(),ownerId);return metrics.record("execution",()->require(providers.resolve(r.brokerAccountId()),ExecutionCapability.class).execute(r));}}
    @Service public static final class CancelOrderService {private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;private final com.hope.trading.broker_service.connection.application.BrokerConnectionRepository connections;public CancelOrderService(BrokerProviderResolver p,BrokerOperationsMetrics m,com.hope.trading.broker_service.connection.application.BrokerConnectionRepository c){providers=p;metrics=m;connections=c;}public void cancel(UUID id,String order,UUID ownerId){requireOwnership(connections,id,ownerId);metrics.record("cancel",()->require(providers.resolve(id),OrderCapability.class).cancel(id,order));}}
    @Service public static final class ReconcileExecutionService {private static final Logger log=LoggerFactory.getLogger(ReconcileExecutionService.class);private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;private final com.hope.trading.broker_service.connection.application.BrokerConnectionRepository connections;public ReconcileExecutionService(BrokerProviderResolver p,BrokerOperationsMetrics m,com.hope.trading.broker_service.connection.application.BrokerConnectionRepository c){providers=p;metrics=m;connections=c;}public ReconciliationResult reconcile(ReconciliationRequest r,UUID ownerId){log.info("broker_reconciliation providerAccount={} attempt={}",r.brokerAccountId(),r.executionAttemptId());requireOwnership(connections,r.brokerAccountId(),ownerId);return metrics.record("reconciliation",()->require(providers.resolve(r.brokerAccountId()),ReconciliationCapability.class).reconcile(r));}}
    @Service public static class GetRiskSnapshotService {private final BrokerProviderResolver providers;private final BrokerOperationsMetrics metrics;private final BrokerConnectionRepository connections;public GetRiskSnapshotService(BrokerProviderResolver p,BrokerOperationsMetrics m,BrokerConnectionRepository c){providers=p;metrics=m;connections=c;}public RiskSnapshot get(UUID ownerId,UUID id,java.time.Instant from,java.time.Instant to){Objects.requireNonNull(ownerId,"ownerId");Objects.requireNonNull(from,"from");Objects.requireNonNull(to,"to");if(!from.isBefore(to))throw new IllegalArgumentException("from must be before to");if(connections.findByBrokerAccountIdAndOwnerId(id,ownerId).isEmpty())throw new BrokerAuthorizationException("Broker account is not accessible");return metrics.record("risk_snapshot",()->require(providers.resolve(id),RiskSnapshotCapability.class).snapshot(id,from,to));}}

    @Service public static final class ResolveTargetService {
        private static final Logger log = LoggerFactory.getLogger(ResolveTargetService.class);
        private final BrokerProviderResolver providers;
        private final BrokerOperationsMetrics metrics;
        private final BrokerConnectionRepository connections;
        public ResolveTargetService(BrokerProviderResolver p, BrokerOperationsMetrics m, BrokerConnectionRepository c) { providers = p; metrics = m; connections = c; }
        public ResolvedPositionCloseTarget resolve(ResolveTargetRequest request, UUID ownerId) {
            log.info("broker_resolve_target providerAccount={} reference={}", request.brokerAccountId(), request.brokerPositionReference());
            requireOwnership(connections, request.brokerAccountId(), ownerId);
            return metrics.record("resolve_target", () -> require(providers.resolve(request.brokerAccountId()), PositionManagementCapability.class).resolveTarget(request));
        }
    }
    @Service public static final class ExecuteCloseService {
        private static final Logger log = LoggerFactory.getLogger(ExecuteCloseService.class);
        private final BrokerProviderResolver providers;
        private final BrokerOperationsMetrics metrics;
        private final BrokerConnectionRepository connections;
        public ExecuteCloseService(BrokerProviderResolver p, BrokerOperationsMetrics m, BrokerConnectionRepository c) { providers = p; metrics = m; connections = c; }
        public CloseResult execute(ExecuteCloseRequest request, UUID ownerId) {
            log.info("broker_execute_close providerAccount={} scope={}", request.brokerAccountId(), request.resolvedMutationScope());
            requireOwnership(connections, request.brokerAccountId(), ownerId);
            return metrics.record("execute_close", () -> require(providers.resolve(request.brokerAccountId()), PositionManagementCapability.class).executeClose(request));
        }
    }
    @Service public static final class ReconcileCloseService {
        private static final Logger log = LoggerFactory.getLogger(ReconcileCloseService.class);
        private final BrokerProviderResolver providers;
        private final BrokerOperationsMetrics metrics;
        private final BrokerConnectionRepository connections;
        public ReconcileCloseService(BrokerProviderResolver p, BrokerOperationsMetrics m, BrokerConnectionRepository c) { providers = p; metrics = m; connections = c; }
        public ReconciliationCloseResult reconcile(ReconcileCloseRequest request, UUID ownerId) {
            log.info("broker_reconcile_close providerAccount={} scope={}", request.brokerAccountId(), request.resolvedMutationScope());
            requireOwnership(connections, request.brokerAccountId(), ownerId);
            return metrics.record("reconcile_close", () -> require(providers.resolve(request.brokerAccountId()), PositionManagementCapability.class).reconcile(request));
        }
    }
}

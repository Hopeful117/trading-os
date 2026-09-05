package com.hope.trading.trading_core.positionclose.infrastructure.adapter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "broker-service", contextId = "position-close-client", url = "${broker-service.base-url:}")
public interface BrokerPositionCloseClient {
    @PostMapping("/internal/v1/positions/resolve-target")
    ResolvedTargetResponse resolveTarget(@RequestBody ResolveTargetRequest request);

    @PostMapping("/internal/v1/positions/execute-close")
    BrokerCloseResponse executeClose(@RequestBody ExecuteCloseRequest request);

    @PostMapping("/internal/v1/positions/reconcile-close")
    BrokerReconcileResponse reconcileClose(@RequestBody ReconcileCloseRequest request);

    record ResolveTargetRequest(UUID brokerAccountId, String brokerPositionReference) {}
    record ResolvedTargetResponse(UUID brokerAccountId, String resolvedMutationScope) {}
    record ExecuteCloseRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {}
    record BrokerCloseResponse(String outcome, String externalOrderId, String correlationId, String status, String reasonCode) {}
    record ReconcileCloseRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {}
    record BrokerReconcileResponse(String outcome, String reconciliationResult) {}
}
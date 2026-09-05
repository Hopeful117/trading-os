package com.hope.trading.trading_core.positionclose.application.port;

import java.util.UUID;

public interface BrokerPositionClosePort {
    ResolveTargetResponse resolveTarget(UUID accountId, String brokerPositionReference);

    BrokerCloseResponse executeClose(String resolvedMutationScope, String idempotencyKey);

    BrokerReconcileResponse reconcileClose(String resolvedMutationScope, String idempotencyKey);

    record ResolveTargetResponse(UUID brokerAccountId, String resolvedMutationScope) {}

    record BrokerCloseResponse(String outcome, String externalOrderId, String correlationId, String status, String reasonCode) {}

    record BrokerReconcileResponse(String outcome, String reconciliationResult) {}
}
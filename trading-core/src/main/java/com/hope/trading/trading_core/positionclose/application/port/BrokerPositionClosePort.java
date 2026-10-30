package com.hope.trading.trading_core.positionclose.application.port;

import java.util.UUID;
import java.math.BigDecimal;

public interface BrokerPositionClosePort {
    ResolveTargetResponse resolveTarget(UUID accountId, String brokerPositionReference);

    default BrokerCloseResponse executeClose(String resolvedMutationScope, String idempotencyKey) {
        String[] parts = resolvedMutationScope.split(":", 2);
        return executeClose(UUID.fromString(parts[0]), resolvedMutationScope, null, idempotencyKey);
    }

    BrokerCloseResponse executeClose(UUID brokerAccountId, String resolvedMutationScope,
                                     BigDecimal closeQuantity, String idempotencyKey);

    default BrokerReconcileResponse reconcileClose(String resolvedMutationScope, String idempotencyKey) {
        String[] parts = resolvedMutationScope.split(":", 2);
        return reconcileClose(UUID.fromString(parts[0]), resolvedMutationScope, idempotencyKey);
    }

    BrokerReconcileResponse reconcileClose(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey);

    record ResolveTargetResponse(UUID brokerAccountId, String resolvedMutationScope) {}

    record BrokerCloseResponse(String outcome, String externalOrderId, String correlationId, String status, String reasonCode) {}

    record BrokerReconcileResponse(String outcome, String reconciliationResult,
                                   BigDecimal remainingQuantity) {
        public BrokerReconcileResponse(String outcome, String reconciliationResult) {
            this(outcome, reconciliationResult, null);
        }
    }
}

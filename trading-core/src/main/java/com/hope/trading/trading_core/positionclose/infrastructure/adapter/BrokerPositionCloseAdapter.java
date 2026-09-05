package com.hope.trading.trading_core.positionclose.infrastructure.adapter;

import com.hope.trading.trading_core.positionclose.application.port.BrokerPositionClosePort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BrokerPositionCloseAdapter implements BrokerPositionClosePort {
    private final BrokerPositionCloseClient client;

    public BrokerPositionCloseAdapter(BrokerPositionCloseClient client) {
        this.client = client;
    }

    @Override
    public ResolveTargetResponse resolveTarget(UUID accountId, String brokerPositionReference) {
        var request = new BrokerPositionCloseClient.ResolveTargetRequest(accountId, brokerPositionReference);
        var response = client.resolveTarget(request);
        return new ResolveTargetResponse(response.brokerAccountId(), response.resolvedMutationScope());
    }

    @Override
    public BrokerCloseResponse executeClose(String resolvedMutationScope, String idempotencyKey) {
        String[] parts = resolvedMutationScope.split(":");
        UUID brokerAccountId = UUID.fromString(parts[0]);
        var request = new BrokerPositionCloseClient.ExecuteCloseRequest(brokerAccountId, resolvedMutationScope, idempotencyKey);
        var response = client.executeClose(request);
        return new BrokerCloseResponse(response.outcome(), response.externalOrderId(), response.correlationId(), response.status(), response.reasonCode());
    }

    @Override
    public BrokerReconcileResponse reconcileClose(String resolvedMutationScope, String idempotencyKey) {
        String[] parts = resolvedMutationScope.split(":");
        UUID brokerAccountId = UUID.fromString(parts[0]);
        var request = new BrokerPositionCloseClient.ReconcileCloseRequest(brokerAccountId, resolvedMutationScope, idempotencyKey);
        var response = client.reconcileClose(request);
        return new BrokerReconcileResponse(response.outcome(), response.reconciliationResult());
    }
}
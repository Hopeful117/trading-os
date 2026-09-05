package com.hope.trading.trading_core.positionclose.infrastructure.adapter;

import com.hope.trading.trading_core.positionclose.application.port.BrokerPositionClosePort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrokerPositionCloseAdapterTest {

    private final BrokerPositionCloseClient client = mock(BrokerPositionCloseClient.class);
    private final BrokerPositionCloseAdapter adapter = new BrokerPositionCloseAdapter(client);

    @Test
    void resolveTarget_delegatesToClientAndMapsResponse() {
        UUID accountId = UUID.randomUUID();
        String brokerPositionReference = "BTC-POS-1";

        UUID brokerAccountId = UUID.randomUUID();
        String resolvedMutationScope = brokerAccountId + ":spot:close";

        when(client.resolveTarget(any(BrokerPositionCloseClient.ResolveTargetRequest.class)))
                .thenReturn(new BrokerPositionCloseClient.ResolvedTargetResponse(brokerAccountId, resolvedMutationScope));

        BrokerPositionClosePort.ResolveTargetResponse response =
                adapter.resolveTarget(accountId, brokerPositionReference);

        assertThat(response.brokerAccountId()).isEqualTo(brokerAccountId);
        assertThat(response.resolvedMutationScope()).isEqualTo(resolvedMutationScope);

        verify(client).resolveTarget(argThat(req ->
                req.brokerAccountId().equals(accountId) && req.brokerPositionReference().equals(brokerPositionReference)));
    }

    @Test
    void executeClose_parsesBrokerAccountIdAndDelegates() {
        UUID brokerAccountId = UUID.randomUUID();
        String resolvedMutationScope = brokerAccountId + ":spot:close";
        String idempotencyKey = "idemp-123";

        when(client.executeClose(any(BrokerPositionCloseClient.ExecuteCloseRequest.class)))
                .thenReturn(new BrokerPositionCloseClient.BrokerCloseResponse(
                        "ACKNOWLEDGED", "ext-order-1", "corr-1", "PENDING", null));

        BrokerPositionClosePort.BrokerCloseResponse response =
                adapter.executeClose(resolvedMutationScope, idempotencyKey);

        assertThat(response.outcome()).isEqualTo("ACKNOWLEDGED");
        assertThat(response.externalOrderId()).isEqualTo("ext-order-1");
        assertThat(response.correlationId()).isEqualTo("corr-1");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.reasonCode()).isNull();

        verify(client).executeClose(argThat(req ->
                req.brokerAccountId().equals(brokerAccountId)
                        && req.resolvedMutationScope().equals(resolvedMutationScope)
                        && req.idempotencyKey().equals(idempotencyKey)));
    }

    @Test
    void reconcileClose_parsesBrokerAccountIdAndDelegates() {
        UUID brokerAccountId = UUID.randomUUID();
        String resolvedMutationScope = brokerAccountId + ":spot:close";
        String idempotencyKey = "idemp-456";

        when(client.reconcileClose(any(BrokerPositionCloseClient.ReconcileCloseRequest.class)))
                .thenReturn(new BrokerPositionCloseClient.BrokerReconcileResponse(
                        "RESOLVED", "EXPOSURE_CONFIRMED_ABSENT"));

        BrokerPositionClosePort.BrokerReconcileResponse response =
                adapter.reconcileClose(resolvedMutationScope, idempotencyKey);

        assertThat(response.outcome()).isEqualTo("RESOLVED");
        assertThat(response.reconciliationResult()).isEqualTo("EXPOSURE_CONFIRMED_ABSENT");

        verify(client).reconcileClose(argThat(req ->
                req.brokerAccountId().equals(brokerAccountId)
                        && req.resolvedMutationScope().equals(resolvedMutationScope)
                        && req.idempotencyKey().equals(idempotencyKey)));
    }
}

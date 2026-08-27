package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthorizationException;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BrokerOwnershipTest {

    private BrokerConnectionRepository connections;
    private ExecuteOrderService executeService;
    private CancelOrderService cancelService;
    private ReconcileExecutionService reconcileService;

    private final UUID brokerAccountId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        connections = mock(BrokerConnectionRepository.class);
        // Pass null for providers/metrics — ownership check throws before they are used
        executeService = new ExecuteOrderService(null, null, connections);
        cancelService = new CancelOrderService(null, null, connections);
        reconcileService = new ReconcileExecutionService(null, null, connections);
    }

    @Test
    void execute_rejectsWrongOwner() {
        when(connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, otherUserId))
                .thenReturn(Optional.empty());

        ExecutionRequest request = new ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "key-1",
                brokerAccountId, "BTCUSD", Side.BUY,
                OrderType.MARKET, BigDecimal.ONE, null);

        assertThatThrownBy(() -> executeService.execute(request, otherUserId))
                .isInstanceOf(BrokerAuthorizationException.class);
    }

    @Test
    void cancel_rejectsWrongOwner() {
        when(connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelService.cancel(brokerAccountId, "order-1", otherUserId))
                .isInstanceOf(BrokerAuthorizationException.class);
    }

    @Test
    void reconcile_rejectsWrongOwner() {
        when(connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, otherUserId))
                .thenReturn(Optional.empty());

        ReconciliationRequest request = new ReconciliationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "key-1", brokerAccountId);

        assertThatThrownBy(() -> reconcileService.reconcile(request, otherUserId))
                .isInstanceOf(BrokerAuthorizationException.class);
    }
}

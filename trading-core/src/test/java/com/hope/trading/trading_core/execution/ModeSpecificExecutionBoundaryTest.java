package com.hope.trading.trading_core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionAdapter;
import com.hope.trading.trading_core.execution.infrastructure.adapter.RoutingBrokerExecutionAdapter;
import com.hope.trading.trading_core.execution.infrastructure.adapter.SimulatedExecutionAdapter;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModeSpecificExecutionBoundaryTest {
    @Test
    void paperDoesNotClaimExternalReconciliation() {
        SimulatedExecutionAdapter adapter = new SimulatedExecutionAdapter(
                mock(BrokerAccountRepository.class), mock(MarketDataClient.class));

        BrokerExecutionPort.ReconciliationResult result = adapter.reconcile(
                new BrokerExecutionPort.ReconciliationRequest(
                        new com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId(UUID.randomUUID()),
                        new com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId(UUID.randomUUID()),
                        new com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey("paper-reconcile"),
                        UUID.randomUUID()));

        assertThat(result).isEqualTo(new BrokerExecutionPort.Inconsistent("PAPER_RECONCILIATION_NOT_SUPPORTED"));
    }

    @Test
    void simulatedAdapterRejectsLiveBeforeRequestingMarketData() {
        UUID brokerAccountId = UUID.randomUUID();
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(brokers.findById(brokerAccountId)).thenReturn(Optional.of(BrokerAccount.create(
                UUID.randomUUID(), UUID.randomUUID(), ExecutionMode.LIVE, "External demo", Instant.now())));

        SimulatedExecutionAdapter adapter = new SimulatedExecutionAdapter(brokers, marketData);
        BrokerExecutionPort.SubmissionResult result = adapter.submit(new BrokerExecutionPort.ExecutionRequest(
                ExecutionIntentId.newId(), ExecutionAttemptId.newId(), new IdempotencyKey("live-not-paper"),
                brokerAccountId, new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                ExecutionParameters.OrderType.MARKET, BigDecimal.ONE, null)));

        assertThat(result).isEqualTo(new BrokerExecutionPort.Rejected(null, "NOT_A_PAPER_ACCOUNT"));
        verifyNoInteractions(marketData);
    }

    @Test
    void liveRoutingUsesExternalAdapterAndNeverUsesSimulator() {
        UUID brokerAccountId = UUID.randomUUID();
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        BrokerExecutionAdapter liveAdapter = mock(BrokerExecutionAdapter.class);
        SimulatedExecutionAdapter paperAdapter = mock(SimulatedExecutionAdapter.class);
        RoutingBrokerExecutionAdapter routing = new RoutingBrokerExecutionAdapter(
                liveAdapter, paperAdapter, brokers);
        when(brokers.findById(brokerAccountId)).thenReturn(Optional.of(BrokerAccount.create(
                UUID.randomUUID(), UUID.randomUUID(), ExecutionMode.LIVE, "External demo", Instant.now())));

        BrokerExecutionPort.ExecutionRequest request = new BrokerExecutionPort.ExecutionRequest(
                ExecutionIntentId.newId(), ExecutionAttemptId.newId(), new IdempotencyKey("live-routing"),
                brokerAccountId, new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                ExecutionParameters.OrderType.MARKET, BigDecimal.ONE, null));
        BrokerExecutionPort.SubmissionResult expected =
                new BrokerExecutionPort.Rejected(null, "EXTERNAL_ADAPTER_TEST");
        when(liveAdapter.submit(request)).thenReturn(expected);

        assertThat(routing.submit(request)).isEqualTo(expected);
        verify(liveAdapter).submit(request);
        verifyNoInteractions(paperAdapter);
    }

    @Test
    void liveRoutingUsesExternalAdapterForCancelAndNeverUsesSimulator() {
        UUID brokerAccountId = UUID.randomUUID();
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        BrokerExecutionAdapter liveAdapter = mock(BrokerExecutionAdapter.class);
        SimulatedExecutionAdapter paperAdapter = mock(SimulatedExecutionAdapter.class);
        RoutingBrokerExecutionAdapter routing = new RoutingBrokerExecutionAdapter(
                liveAdapter, paperAdapter, brokers);
        when(brokers.findById(brokerAccountId)).thenReturn(Optional.of(BrokerAccount.create(
                UUID.randomUUID(), UUID.randomUUID(), ExecutionMode.LIVE, "External demo", Instant.now())));

        routing.cancel(brokerAccountId, "external-order-1");

        verify(liveAdapter).cancel(brokerAccountId, "external-order-1");
        verifyNoInteractions(paperAdapter);
    }

    @Test
    void liveRoutingUsesExternalAdapterForReconciliationAndNeverUsesSimulator() {
        UUID brokerAccountId = UUID.randomUUID();
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        BrokerExecutionAdapter liveAdapter = mock(BrokerExecutionAdapter.class);
        SimulatedExecutionAdapter paperAdapter = mock(SimulatedExecutionAdapter.class);
        RoutingBrokerExecutionAdapter routing = new RoutingBrokerExecutionAdapter(
                liveAdapter, paperAdapter, brokers);
        when(brokers.findById(brokerAccountId)).thenReturn(Optional.of(BrokerAccount.create(
                UUID.randomUUID(), UUID.randomUUID(), ExecutionMode.LIVE, "External demo", Instant.now())));
        BrokerExecutionPort.ReconciliationRequest request = new BrokerExecutionPort.ReconciliationRequest(
                ExecutionIntentId.newId(), ExecutionAttemptId.newId(), new IdempotencyKey("live-reconcile"),
                brokerAccountId);
        BrokerExecutionPort.ReconciliationResult expected =
                new BrokerExecutionPort.Inconsistent("EXTERNAL_ADAPTER_TEST");
        when(liveAdapter.reconcile(request)).thenReturn(expected);

        assertThat(routing.reconcile(request)).isEqualTo(expected);
        verify(liveAdapter).reconcile(request);
        verifyNoInteractions(paperAdapter);
    }
}

package com.hope.trading.trading_core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.infrastructure.adapter.SimulatedExecutionAdapter;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
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
}

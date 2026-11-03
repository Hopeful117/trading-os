package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerAccountReadOnlyStatusServiceTest {
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private final BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
    private final BrokerAccountSnapshotV2Client snapshots = mock(BrokerAccountSnapshotV2Client.class);
    private final BrokerAccountReadOnlyStatusService service =
            new BrokerAccountReadOnlyStatusService(brokerAccounts, snapshots);

    @Test
    void exposesIncompleteProviderFactsAsPartialReadOnlyWithoutCapabilities() {
        BrokerAccount account = BrokerAccount.importConnected(ACCOUNT_ID, OWNER_ID, PROVIDER_ID,
                ExecutionMode.LIVE, "FTMO", "49411967", Instant.now());
        when(brokerAccounts.findByIdAndOwnerId(ACCOUNT_ID, OWNER_ID)).thenReturn(java.util.Optional.of(account));
        var snapshot = new BrokerAccountSnapshotV2Dto(ACCOUNT_ID, PROVIDER_ID, "49411967",
                Map.of("USD", BigDecimal.TEN), List.of(), Instant.now(), null, List.of(), List.of(),
                "INCOMPLETE", "FRESH", Instant.now(), Instant.now(),
                List.of("EQUITY_UNAVAILABLE", "FREE_MARGIN_UNAVAILABLE"));
        when(snapshots.getAccountSnapshot(OWNER_ID, ACCOUNT_ID)).thenReturn(snapshot);

        var result = service.status(OWNER_ID, ACCOUNT_ID);

        assertThat(result.status()).isEqualTo("CONNECTED_BUT_FACTS_INCOMPLETE");
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.riskEvaluationAllowed()).isFalse();
        assertThat(result.snapshot()).isSameAs(snapshot);
    }
}

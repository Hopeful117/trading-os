package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.api.BrokerAccountReadOnlyStatusResponse;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BrokerAccountReadOnlyStatusService {
    private final BrokerAccountRepository brokerAccounts;
    private final BrokerAccountSnapshotV2Client snapshots;

    public BrokerAccountReadOnlyStatusService(BrokerAccountRepository brokerAccounts,
                                              BrokerAccountSnapshotV2Client snapshots) {
        this.brokerAccounts = brokerAccounts;
        this.snapshots = snapshots;
    }

    public BrokerAccountReadOnlyStatusResponse status(UUID ownerId, UUID brokerAccountId) {
        var brokerAccount = brokerAccounts.findByIdAndOwnerId(brokerAccountId, ownerId)
                .orElseThrow(BrokerAccountNotFoundException::new);
        if (brokerAccount.connectionStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new IllegalStateException("Broker account is not connected");
        }

        BrokerAccountSnapshotV2Dto snapshot = snapshots.getAccountSnapshot(ownerId, brokerAccountId);
        String status = completeAndFresh(snapshot)
                ? "READ_ONLY_COMPLETE"
                : "CONNECTED_BUT_FACTS_INCOMPLETE";
        return new BrokerAccountReadOnlyStatusResponse(status, false, false, snapshot);
    }

    private boolean completeAndFresh(BrokerAccountSnapshotV2Dto snapshot) {
        return snapshot != null
                && "COMPLETE".equals(snapshot.completeness())
                && "FRESH".equals(snapshot.freshness());
    }
}

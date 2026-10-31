package com.hope.trading.trading_core.broker.service;

import java.util.UUID;

public interface BrokerSynchronizationService {
    void synchronize(String username);
    void synchronize(UUID ownerId, UUID brokerAccountId);
    LiveCfdReconciliationService.Result reconcileLiveCfd(UUID ownerId, UUID brokerAccountId);
}

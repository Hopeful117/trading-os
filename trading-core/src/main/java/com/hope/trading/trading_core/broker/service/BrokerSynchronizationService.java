package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.dto.BrokerAccountSynchronizationResult;

import java.util.UUID;

public interface BrokerSynchronizationService {
    void synchronize(String username);
    BrokerAccountSynchronizationResult synchronize(UUID ownerId, UUID brokerAccountId);
    LiveCfdReconciliationService.Result reconcileLiveCfd(UUID ownerId, UUID brokerAccountId);
}

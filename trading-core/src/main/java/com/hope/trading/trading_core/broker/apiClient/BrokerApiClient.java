package com.hope.trading.trading_core.broker.apiClient;

import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;

public interface BrokerApiClient {
    BrokerAccountDto getAccount();
}

package com.hope.trading.trading_core.apiClient;

import com.hope.trading.trading_core.dto.BrokerAccountDto;

public interface BrokerApiClient {
    BrokerAccountDto getAccount();
}

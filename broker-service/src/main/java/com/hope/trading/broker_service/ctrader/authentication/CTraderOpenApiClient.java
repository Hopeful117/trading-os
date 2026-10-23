package com.hope.trading.broker_service.ctrader.authentication;

import java.util.List;

public interface CTraderOpenApiClient {
    List<String> discoverAuthorizedAccountIds(CTraderToken token);

    void authorizeAccount(CTraderToken token, String externalAccountId);
}

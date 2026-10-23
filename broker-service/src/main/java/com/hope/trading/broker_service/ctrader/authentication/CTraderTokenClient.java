package com.hope.trading.broker_service.ctrader.authentication;

public interface CTraderTokenClient {
    CTraderToken exchangeAuthorizationCode(String code, String redirectUri);
}

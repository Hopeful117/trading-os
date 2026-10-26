package com.hope.trading.market_data.ctrader.authentication;

public class CTraderAuthorizationException extends IllegalStateException {
    public CTraderAuthorizationException(String message) {
        super(message);
    }

    public CTraderAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

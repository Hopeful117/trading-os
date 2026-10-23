package com.hope.trading.broker_service.ctrader.authentication;

public class CTraderAuthenticationException extends RuntimeException {
    public CTraderAuthenticationException(String message) {
        super(message);
    }

    public CTraderAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

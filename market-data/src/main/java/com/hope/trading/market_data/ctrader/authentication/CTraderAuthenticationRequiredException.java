package com.hope.trading.market_data.ctrader.authentication;

public class CTraderAuthenticationRequiredException extends RuntimeException {
    public CTraderAuthenticationRequiredException() {
        super("cTrader Market Data authentication is required");
    }
}

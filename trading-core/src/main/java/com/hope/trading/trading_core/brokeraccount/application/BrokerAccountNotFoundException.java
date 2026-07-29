package com.hope.trading.trading_core.brokeraccount.application;

public class BrokerAccountNotFoundException extends RuntimeException {
    public BrokerAccountNotFoundException() {
        super("Broker account not found");
    }
}

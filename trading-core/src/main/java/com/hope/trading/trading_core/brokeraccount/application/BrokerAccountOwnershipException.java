package com.hope.trading.trading_core.brokeraccount.application;

public class BrokerAccountOwnershipException extends RuntimeException {
    public BrokerAccountOwnershipException() {
        super("Broker account is not accessible");
    }
}

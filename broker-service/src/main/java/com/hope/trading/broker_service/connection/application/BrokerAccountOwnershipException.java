package com.hope.trading.broker_service.connection.application;

public class BrokerAccountOwnershipException extends RuntimeException {
    public BrokerAccountOwnershipException() { super("Broker account is not accessible"); }
}

package com.hope.trading.broker_service.connection.application;

public class BrokerConnectionRevokedException extends RuntimeException {
    public BrokerConnectionRevokedException() {
        super("Broker connection has been revoked and cannot be recovered");
    }
}

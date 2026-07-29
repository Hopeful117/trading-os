package com.hope.trading.broker_service.connection.application;

public class BrokerConnectionNotFoundException extends RuntimeException {
    public BrokerConnectionNotFoundException() { super("Broker connection not found"); }
}

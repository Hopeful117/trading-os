package com.hope.trading.trading_core.brokeraccount.domain;

public class InvalidBrokerConnectionTransitionException extends RuntimeException {
    public InvalidBrokerConnectionTransitionException(BrokerConnectionStatus from, BrokerConnectionStatus to) {
        super("Broker connection cannot transition from " + from + " to " + to);
    }
}

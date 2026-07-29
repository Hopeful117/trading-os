package com.hope.trading.broker_service.secret.application;

public class SecretRevokedException extends RuntimeException {
    public SecretRevokedException() { super("Broker credentials are no longer active"); }
}

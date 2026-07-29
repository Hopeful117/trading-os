package com.hope.trading.broker_service.secret.application;

public class ConcurrentCredentialRotationException extends RuntimeException {
    public ConcurrentCredentialRotationException() { super("Broker credentials changed concurrently"); }
}

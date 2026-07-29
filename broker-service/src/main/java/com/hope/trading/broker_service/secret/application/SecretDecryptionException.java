package com.hope.trading.broker_service.secret.application;

public class SecretDecryptionException extends RuntimeException {
    public SecretDecryptionException() { super("Unable to resolve protected broker credentials"); }
}

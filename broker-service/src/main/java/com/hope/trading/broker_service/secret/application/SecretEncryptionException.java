package com.hope.trading.broker_service.secret.application;

public class SecretEncryptionException extends RuntimeException {
    public SecretEncryptionException() { super("Unable to protect broker credentials"); }
}

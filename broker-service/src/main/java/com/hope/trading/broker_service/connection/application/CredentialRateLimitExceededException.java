package com.hope.trading.broker_service.connection.application;

public class CredentialRateLimitExceededException extends RuntimeException {
    public CredentialRateLimitExceededException() {
        super("Too many broker credential validation requests");
    }
}

package com.hope.trading.broker_service.credential.domain;

public enum CredentialValidationOutcome {
    VALID,
    INVALID_CREDENTIALS,
    INSUFFICIENT_PERMISSIONS,
    BROKER_UNAVAILABLE,
    RATE_LIMITED,
    UNSUPPORTED_CREDENTIAL_FORMAT,
    UNEXPECTED_PROVIDER_RESPONSE
}

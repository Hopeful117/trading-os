package com.hope.trading.broker_service.connection.domain;

public enum BrokerConnectionStatus {
    CREATED,
    PENDING_VALIDATION,
    CONNECTED,
    INVALID_CREDENTIALS,
    INSUFFICIENT_PERMISSIONS,
    TEMPORARILY_UNAVAILABLE,
    REAUTHENTICATION_REQUIRED,
    DISCONNECTED,
    REVOKED
}

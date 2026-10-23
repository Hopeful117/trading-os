package com.hope.trading.broker_service.ctrader.authentication;

public enum CTraderAuthorizationAttemptStatus {
    INITIATED,
    ACCOUNT_SELECTION_REQUIRED,
    BOUND,
    FAILED,
    EXPIRED,
    REVOKED
}

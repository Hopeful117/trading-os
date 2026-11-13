package com.hope.trading.trading_core.executionauthorization;

public enum ExecutionAuthorizationStatus {
    PENDING_HUMAN_CONFIRMATION,
    APPROVED,
    REJECTED,
    EXPIRED,
    REVOKED,
    CONSUMING,
    CONSUMED
}

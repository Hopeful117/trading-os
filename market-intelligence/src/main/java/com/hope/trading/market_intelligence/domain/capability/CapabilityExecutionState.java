package com.hope.trading.market_intelligence.domain.capability;

public enum CapabilityExecutionState {
    CREATED, WAITING_FOR_REQUIREMENTS, READY, RUNNING,
    COMPLETED, FAILED, SKIPPED, CANCELLED, TIMED_OUT;
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED
                || this == CANCELLED || this == TIMED_OUT;
    }
}

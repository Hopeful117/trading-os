package com.hope.trading.market_intelligence.domain.passive;

public enum PassiveScanRunStatus {
    REQUESTED,
    DISPATCH_REQUESTED,
    RUNNING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    COMPLETED_NO_WORK;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIALLY_COMPLETED
                || this == FAILED || this == COMPLETED_NO_WORK;
    }
}

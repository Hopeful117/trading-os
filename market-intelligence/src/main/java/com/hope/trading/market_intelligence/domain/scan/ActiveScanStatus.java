package com.hope.trading.market_intelligence.domain.scan;

public enum ActiveScanStatus {
    READY_TO_DISPATCH,
    DISPATCH_REQUESTED,
    COMPLETED_NO_WORK;

    public boolean isTerminal() {
        return this == COMPLETED_NO_WORK;
    }
}

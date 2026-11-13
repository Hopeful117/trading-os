package com.hope.trading.market_intelligence.domain.passive;

public enum PassiveScanMarketStatus {
    REGISTERED,
    DISPATCH_REQUESTED,
    ACCEPTED,
    RUNNING,
    COMPLETED,
    INSUFFICIENT_EVIDENCE,
    FAILED,
    SKIPPED;

    public boolean isTerminal() {
        return this == COMPLETED || this == INSUFFICIENT_EVIDENCE
                || this == FAILED || this == SKIPPED;
    }
}

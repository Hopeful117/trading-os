package com.hope.trading.market_intelligence.domain;

public enum ContextSectionStatus {
    AVAILABLE,
    STALE,
    MISSING,
    UNAVAILABLE,
    UNSUPPORTED,
    INCOMPLETE;

    public boolean blocksAnalysis() {
        return this == MISSING || this == UNAVAILABLE || this == UNSUPPORTED || this == INCOMPLETE;
    }
}

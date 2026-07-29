package com.hope.trading.market_intelligence.domain.execution;

public enum AnalysisExecutionStatus {
    REQUESTED,
    ACCEPTED,
    CONTEXT_BUILDING,
    RUNNING,
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}

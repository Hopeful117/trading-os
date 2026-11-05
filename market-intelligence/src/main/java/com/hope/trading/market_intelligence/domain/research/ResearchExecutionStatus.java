package com.hope.trading.market_intelligence.domain.research;

public enum ResearchExecutionStatus {
    REQUESTED,
    RUNNING,
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED
}

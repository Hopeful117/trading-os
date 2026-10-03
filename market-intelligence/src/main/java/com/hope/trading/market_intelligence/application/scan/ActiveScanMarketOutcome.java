package com.hope.trading.market_intelligence.application.scan;

public enum ActiveScanMarketOutcome {
    EXCLUDED,
    NOT_EVALUABLE,
    RUNNING,
    COMPLETED_NO_OPPORTUNITY,
    OPPORTUNITY_FOUND,
    FAILED,
    CANCELLED,
    EXPIRED
}

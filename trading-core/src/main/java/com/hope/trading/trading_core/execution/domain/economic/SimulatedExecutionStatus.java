package com.hope.trading.trading_core.execution.domain.economic;

public enum SimulatedExecutionStatus {
    FILLED,
    PARTIALLY_FILLED,
    UNFILLED,
    INSUFFICIENT_MARKET_EVIDENCE,
    INSUFFICIENT_DEPTH,
    UNSUPPORTED_ORDER_TYPE,
    STALE_EVIDENCE,
    INVALID_INPUT
}

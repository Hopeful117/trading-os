package com.hope.trading.market_intelligence.strategy.domain;

/**
 * Direction semantics of a strategy definition. DYNAMIC means the concrete
 * LONG or SHORT direction is determined during evaluation from evidence.
 */
public enum StrategyDirection {
    LONG,
    SHORT,
    DYNAMIC
}

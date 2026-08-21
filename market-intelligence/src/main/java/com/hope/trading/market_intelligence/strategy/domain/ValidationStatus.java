package com.hope.trading.market_intelligence.strategy.domain;

/**
 * Truth about whether accepted deterministic validation evidence exists for a
 * strategy version (ADR-034). Independent from the governance lifecycle.
 */
public enum ValidationStatus {
    UNVALIDATED,
    VALIDATED
}

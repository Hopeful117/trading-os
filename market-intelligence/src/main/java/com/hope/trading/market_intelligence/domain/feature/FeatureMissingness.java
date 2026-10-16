package com.hope.trading.market_intelligence.domain.feature;

public enum FeatureMissingness {
    PRESENT,
    MISSING,
    INSUFFICIENT_DATA,
    STALE_INPUT,
    INVALID_INPUT,
    UNAVAILABLE,
    UNSUPPORTED,
    NOT_APPLICABLE
}

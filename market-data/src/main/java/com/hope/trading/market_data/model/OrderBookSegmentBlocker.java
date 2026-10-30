package com.hope.trading.market_data.model;

public enum OrderBookSegmentBlocker {
    OPEN_SEGMENT,
    NO_TRUSTED_SUCCESSOR_BOUNDARY,
    PROTECTED_DEPENDENCY,
    ACTIVE_HOLD,
    UNTRUSTED_ARTIFACT,
    UNKNOWN_LEGACY,
    CONTINUITY_UNPROVEN,
    NO_RELEASED_DEPENDENCY
}

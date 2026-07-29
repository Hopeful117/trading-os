package com.hope.trading.market_intelligence.domain.context;

public enum ContextClassification {
    PUBLIC,
    INTERNAL,
    USER_CONFIDENTIAL,
    TRADING_SENSITIVE,
    RESTRICTED;

    public boolean isAllowedBy(ContextClassification maximum) {
        return ordinal() <= maximum.ordinal();
    }
}

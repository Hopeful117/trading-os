package com.hope.trading.market_intelligence.domain.opportunity;

/** Immutable production context, independent from user preferences. */
public enum OpportunityOrigin {
    PASSIVE_SCAN, ACTIVE_SCAN, USER_REQUEST, SYSTEM_REEVALUATION
}

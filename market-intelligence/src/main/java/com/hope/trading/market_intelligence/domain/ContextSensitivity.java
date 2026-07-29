package com.hope.trading.market_intelligence.domain;

/**
 * Compatibility projection for the ADR-020 wire model. New authorization
 * decisions use the complete ADR-021 classification declared by
 * {@code ContextContributionDescriptor}. Kept as a compatibility field on
 * ADR-020 context sections until their wire contract is versioned.
 */
public enum ContextSensitivity {
    PUBLIC,
    USER_PRIVATE
}

package com.hope.trading.market_intelligence.strategy.domain;

/**
 * Category of a semantic input a strategy requires. Strategy declares WHAT it
 * needs; Market Intelligence decides HOW the input is produced.
 */
public enum SemanticInputType {
    OBSERVATION,
    FEATURE
}

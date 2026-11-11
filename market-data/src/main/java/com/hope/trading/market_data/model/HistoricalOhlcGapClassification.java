package com.hope.trading.market_data.model;

public enum HistoricalOhlcGapClassification {
    EXPECTED_MARKET_CLOSURE,
    MISSING_DATA,
    INVALID_CANDLE,
    DUPLICATE,
    UNCERTAIN
}

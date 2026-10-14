package com.hope.trading.market_data.dto;

public enum MarketPriceSnapshotStatus {
    FRESH,
    STALE,
    STALE_PROVIDER,
    TIMEOUT,
    UNAVAILABLE,
    UNKNOWN_MARKET
}

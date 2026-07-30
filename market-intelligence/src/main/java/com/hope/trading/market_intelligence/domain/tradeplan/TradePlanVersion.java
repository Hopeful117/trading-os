package com.hope.trading.market_intelligence.domain.tradeplan;

public record TradePlanVersion(long value) {
    public TradePlanVersion {
        if (value < 1) throw new IllegalArgumentException("TradePlan version starts at 1");
    }
    public TradePlanVersion next() { return new TradePlanVersion(value + 1); }
}

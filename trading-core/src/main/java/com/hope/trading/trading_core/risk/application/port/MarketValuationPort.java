package com.hope.trading.trading_core.risk.application.port;

/**
 * Compatibility facade for Risk Domain callers. The valuation contract belongs
 * to the shared Market Data integration boundary.
 */
public interface MarketValuationPort
        extends com.hope.trading.trading_core.market_data.valuation.MarketValuationPort {
}

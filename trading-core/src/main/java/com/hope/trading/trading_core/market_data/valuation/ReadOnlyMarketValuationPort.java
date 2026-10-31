package com.hope.trading.trading_core.market_data.valuation;

import java.time.Instant;
import java.util.List;

/** Read-side valuation facts that must not create persisted valuation snapshots. */
public interface ReadOnlyMarketValuationPort {
    MarketValuationPort.Snapshot valueReadOnly(String reportingCurrency, Instant at,
                                               List<MarketValuationPort.Instrument> instruments,
                                               List<MarketValuationPort.Asset> assets,
                                               MarketValuationPort.FreshnessRequirement requirement);
}

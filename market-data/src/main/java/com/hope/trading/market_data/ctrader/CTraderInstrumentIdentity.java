package com.hope.trading.market_data.ctrader;

import java.util.Objects;

/** Provider-side identity kept at the Market Data adapter boundary. */
public record CTraderInstrumentIdentity(long symbolId, String symbolName, String environment) {
    public CTraderInstrumentIdentity {
        if (symbolId <= 0) throw new IllegalArgumentException("symbolId must be positive");
        if (symbolName == null || symbolName.isBlank()) throw new IllegalArgumentException("symbolName is required");
        if (environment == null || environment.isBlank()) throw new IllegalArgumentException("environment is required");
    }

    public String provenance() {
        return "cTrader:" + environment + ":" + symbolId + ":" + Objects.requireNonNull(symbolName);
    }
}

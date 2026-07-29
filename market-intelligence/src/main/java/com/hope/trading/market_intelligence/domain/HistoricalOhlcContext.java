package com.hope.trading.market_intelligence.domain;

import java.util.List;
import java.util.UUID;

public record HistoricalOhlcContext(
        UUID marketId,
        String interval,
        List<OhlcPoint> candles
) implements ContextPayload {
    public HistoricalOhlcContext {
        candles = List.copyOf(candles);
    }
}

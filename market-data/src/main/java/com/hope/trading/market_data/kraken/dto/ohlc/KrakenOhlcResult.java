package com.hope.trading.market_data.kraken.dto.ohlc;

import java.time.Instant;
import java.util.List;

public record KrakenOhlcResult(
        String providerSymbol,
        List<KrakenRestOhlcEntry> entries,
        Instant last

) {
}

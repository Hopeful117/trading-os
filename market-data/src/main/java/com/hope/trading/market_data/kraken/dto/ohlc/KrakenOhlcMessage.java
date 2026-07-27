package com.hope.trading.market_data.kraken.dto.ohlc;

import com.hope.trading.market_data.kraken.dto.KrakenChannel;
import com.hope.trading.market_data.kraken.dto.KrakenMessageType;

import java.time.Instant;
import java.util.List;

public record KrakenOhlcMessage(
        KrakenChannel channel,
        KrakenMessageType type,
        Instant timestamp,
        List<KrakenOhlcEntry> data


) {
}

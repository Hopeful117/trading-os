package com.hope.trading.market_data.kraken.dto.orderbook;

import com.hope.trading.market_data.kraken.dto.KrakenChannel;
import com.hope.trading.market_data.kraken.dto.KrakenMessageType;

import java.util.List;

public record KrakenOrderBookMessage(
        KrakenChannel channel,
        KrakenMessageType type,
        List<KrakenOrderBookData> data
) {
}

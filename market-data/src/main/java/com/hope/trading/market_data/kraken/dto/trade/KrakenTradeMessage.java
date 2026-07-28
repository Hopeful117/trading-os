package com.hope.trading.market_data.kraken.dto.trade;

import com.hope.trading.market_data.kraken.dto.KrakenChannel;
import com.hope.trading.market_data.kraken.dto.KrakenMessageType;

import java.util.List;

public record KrakenTradeMessage(
        KrakenChannel channel,
        KrakenMessageType type,
        List<KrakenTradeData> data
) {
}

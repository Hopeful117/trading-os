package com.hope.trading.market_data.kraken.dto.ticker;

import com.hope.trading.market_data.kraken.dto.KrakenChannel;
import com.hope.trading.market_data.kraken.dto.KrakenMessageType;
import lombok.Data;

import java.util.List;

@Data
public class KrakenTickerMessage {
    private KrakenChannel channel;

    private KrakenMessageType type;

    private List<KrakenTickerData> data;

}

package com.hope.trading.market_data.kraken.dto;

import lombok.Data;

import java.util.List;

@Data
public class KrakenTickerMessage {
    private String channel;

    private String type;

    private List<KrakenTickerData> data;

}

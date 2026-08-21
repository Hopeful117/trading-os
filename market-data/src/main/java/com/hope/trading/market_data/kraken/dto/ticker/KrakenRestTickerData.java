package com.hope.trading.market_data.kraken.dto.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class KrakenRestTickerData {
    @JsonProperty("a")
    private List<String> ask;

    @JsonProperty("b")
    private List<String> bid;

    @JsonProperty("c")
    private List<String> lastTrade;

    @JsonProperty("v")
    private List<String> volume;
}

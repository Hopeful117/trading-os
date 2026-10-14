package com.hope.trading.market_data.kraken.dto.futures;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class KrakenFuturesChartResponse {
    private List<KrakenFuturesChartCandle> candles;

    @JsonProperty("more_candles")
    private Boolean moreAvailable;
}

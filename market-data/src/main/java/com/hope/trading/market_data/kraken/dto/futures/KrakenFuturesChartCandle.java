package com.hope.trading.market_data.kraken.dto.futures;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/** Kraken Futures Charts API candle. The API timestamps candles in epoch milliseconds. */
@Data
public class KrakenFuturesChartCandle {
    private Long time;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private BigDecimal vwap;
    private Integer trades;

    @JsonProperty("num_trades")
    public void setNumTrades(Integer trades) {
        this.trades = trades;
    }
}

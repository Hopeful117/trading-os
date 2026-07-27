package com.hope.trading.market_data.kraken.dto.ticker;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KrakenTickerData {

    private String symbol;

    private BigDecimal bid;

    private BigDecimal bidQty;

    private BigDecimal ask;

    private BigDecimal askQty;

    private BigDecimal last;

    private BigDecimal volume;

    private BigDecimal vwap;

    private BigDecimal low;

    private BigDecimal high;

    private BigDecimal change;

    private BigDecimal changePct;
}

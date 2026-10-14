package com.hope.trading.market_data.kraken.dto.futures;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class KrakenFuturesTickerDto {
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal last;
    private Instant lastTime;
    private String tag;
    private String pair;
}

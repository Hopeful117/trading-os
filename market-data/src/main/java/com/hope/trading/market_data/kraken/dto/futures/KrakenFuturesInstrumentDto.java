package com.hope.trading.market_data.kraken.dto.futures;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class KrakenFuturesInstrumentDto {
    private String symbol;
    private String pair;
    private String base;
    private String quote;
    private String type;
    private BigDecimal tickSize;
    private BigDecimal contractSize;
    private Integer contractValueTradePrecision;
    private Boolean tradeable;
    private Boolean isExpired;
    private Instant lastTradingTime;
}

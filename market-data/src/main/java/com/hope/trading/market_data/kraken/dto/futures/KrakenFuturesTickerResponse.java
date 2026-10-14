package com.hope.trading.market_data.kraken.dto.futures;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class KrakenFuturesTickerResponse {
    private String result;
    private Instant serverTime;
    private List<KrakenFuturesTickerDto> tickers;
}

package com.hope.trading.market_data.kraken.dto.futures;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class KrakenFuturesInstrumentStatusResponse {
    private String result;
    private Instant serverTime;
    private List<KrakenFuturesInstrumentStatusDto> instrumentStatus;
}

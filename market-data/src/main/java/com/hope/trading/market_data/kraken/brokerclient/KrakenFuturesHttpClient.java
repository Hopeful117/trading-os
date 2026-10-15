package com.hope.trading.market_data.kraken.brokerclient;

import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentResponse;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentStatusResponse;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "kraken-futures-client",
        url = "${kraken.futures-base-url}"
)
public interface KrakenFuturesHttpClient {
    @GetMapping("/derivatives/api/v3/instruments")
    KrakenFuturesInstrumentResponse getInstruments();

    @GetMapping("/derivatives/api/v3/instruments/status")
    KrakenFuturesInstrumentStatusResponse getInstrumentStatus();

    @GetMapping("/derivatives/api/v3/tickers")
    KrakenFuturesTickerResponse getTickers();
}

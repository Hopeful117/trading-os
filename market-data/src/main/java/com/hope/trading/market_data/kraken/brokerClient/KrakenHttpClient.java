package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairsResponse;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "kraken-client",
        url = "${kraken.base-url}"
)
public interface KrakenHttpClient {
    @GetMapping("/0/public/AssetPairs")
    KrakenAssetPairsResponse getAssetPairs();

    @GetMapping("/0/public/Ticker")
    KrakenTickerResponse findTicker(
            @RequestParam("pair") String pair
    );

    @GetMapping("/0/public/OHLC")
    KrakenOhlcResponse findOhlcHistory(
            @RequestParam("pair") String pair,
            @RequestParam("interval") int interval
    );

}

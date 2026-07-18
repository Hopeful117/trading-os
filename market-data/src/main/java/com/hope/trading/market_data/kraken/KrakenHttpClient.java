package com.hope.trading.market_data.kraken;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "kraken-client",
        url = "${kraken.base-url}"
)
public interface KrakenHttpClient {
    @GetMapping("/0/public/AssetPairs")
    KrakenAssetPairsResponse getAssetPairs();

}

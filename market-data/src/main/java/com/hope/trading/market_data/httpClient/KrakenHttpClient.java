package com.hope.trading.market_data.httpClient;

import com.hope.trading.market_data.dto.KrakenAssetPairsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "kraken-client",
        url = "${kraken.base-url}"
)
public interface KrakenHttpClient {
    @GetMapping("/0/public/AssetPairs")
    KrakenAssetPairsResponse getAssetPairs();

}

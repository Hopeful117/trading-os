package com.hope.trading.market_intelligence.adapter.marketdata;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "market-data")
public interface MarketDataClient {
    @GetMapping("/api/v1/markets/{marketId}")
    MarketResponse findMarket(@PathVariable UUID marketId);

    @PostMapping("/internal/markets/prices/snapshot")
    List<MarketPriceSnapshotResponse> findPriceSnapshots(
            @RequestBody MarketPriceSnapshotRequest request
    );

    @GetMapping("/api/v1/markets/{marketId}/ohlc")
    List<OhlcResponse> findOhlc(
            @PathVariable UUID marketId,
            @RequestParam String interval,
            @RequestParam int limit
    );
}

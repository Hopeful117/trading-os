package com.hope.trading.trading_core.market_data.apiClient;


import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "market-data"
)
public interface MarketDataClient {
    @GetMapping("/api/v1/markets")
    List<MarketResponse> findAll();

    @GetMapping("/api/v1/markets/{marketId}")
    MarketResponse findById(
            @PathVariable UUID marketId
    );

    @PostMapping("/internal/markets/prices/snapshot")
    List<MarketPriceSnapshotDto> findPriceSnapshots(
            @RequestBody MarketPriceSnapshotRequest request
    );
}

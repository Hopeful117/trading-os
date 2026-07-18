package com.hope.trading.trading_core.market_data.apiClient;


import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "market-data",
        path = "/api/v1/markets"
)
public interface MarketDataClient {
    @GetMapping
    List<MarketResponse> findAll();

    @GetMapping("/{marketId}")
    MarketResponse findById(
            @PathVariable UUID marketId
    );
}

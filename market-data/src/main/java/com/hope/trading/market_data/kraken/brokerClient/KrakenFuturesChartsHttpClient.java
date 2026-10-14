package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "kraken-futures-charts-client",
        url = "${kraken.futures-charts-base-url:https://futures.kraken.com/api/charts/v1}"
)
public interface KrakenFuturesChartsHttpClient {
    @GetMapping("/{tickType}/{symbol}/{resolution}")
    KrakenFuturesChartResponse getCandles(
            @PathVariable String tickType,
            @PathVariable String symbol,
            @PathVariable String resolution,
            @RequestParam(required = false) Integer count,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to
    );
}

package com.hope.trading.market_intelligence.adapter.marketdata;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "market-data", contextId = "historicalOhlcClient",
        configuration = MarketDataFeignConfiguration.class)
public interface HistoricalOhlcClient {
    @GetMapping("/internal/v1/historical-ohlc/{marketId}")
    HistoricalOhlcSeriesResponse findHistoricalOhlc(
            @PathVariable UUID marketId,
            @RequestParam String interval,
            @RequestParam Instant asOf,
            @RequestParam int limit
    );

    record HistoricalOhlcSeriesResponse(
            UUID marketId,
            String interval,
            Instant requestedAsOf,
            List<HistoricalOhlcEvidenceResponse> observations,
            String status,
            String reason
    ) {
        public HistoricalOhlcSeriesResponse {
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }

    record HistoricalOhlcEvidenceResponse(
            UUID observationId,
            UUID marketId,
            String interval,
            Instant openTime,
            Instant closeTime,
            java.math.BigDecimal open,
            java.math.BigDecimal high,
            java.math.BigDecimal low,
            java.math.BigDecimal close,
            java.math.BigDecimal volume,
            java.math.BigDecimal vwap,
            Integer tradeCount,
            boolean closed,
            boolean synthetic,
            String provider,
            String providerSymbol,
            String sourceId,
            Instant sourceUpdatedAt,
            Instant fetchedAt,
            Instant capturedAt,
            Instant knownAt,
            String origin,
            String normalizationVersion,
            String observationFingerprint
    ) { }
}

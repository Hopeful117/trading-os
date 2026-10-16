package com.hope.trading.market_intelligence.adapter.marketdata;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "market-data", contextId = "orderBookReplayClient",
        configuration = MarketDataFeignConfiguration.class)
public interface OrderBookReplayClient {
    @GetMapping("/internal/v1/order-book/replay/{marketId}")
    ReplayResponse replay(
            @PathVariable UUID marketId,
            @RequestParam int depth,
            @RequestParam Instant asOf
    );

    record ReplayResponse(
            String status,
            UUID marketId,
            int depth,
            UUID sequenceId,
            Instant evaluationCutoff,
            Instant latestOccurredAt,
            Instant latestKnownAt,
            Long sourceStartOrdinal,
            Long sourceEndOrdinal,
            String provider,
            String providerSymbol,
            String normalizationVersion,
            String sourceEvidenceFingerprint,
            List<LevelResponse> bids,
            List<LevelResponse> asks,
            String reason
    ) {
        public ReplayResponse {
            bids = bids == null ? List.of() : List.copyOf(bids);
            asks = asks == null ? List.of() : List.copyOf(asks);
        }
    }

    record LevelResponse(BigDecimal price, BigDecimal quantity) { }
}

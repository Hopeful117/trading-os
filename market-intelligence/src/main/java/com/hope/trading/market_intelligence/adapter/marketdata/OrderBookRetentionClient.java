package com.hope.trading.market_intelligence.adapter.marketdata;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "market-data", contextId = "orderBookRetentionClient",
        configuration = MarketDataFeignConfiguration.class)
public interface OrderBookRetentionClient {
    @GetMapping("/internal/v1/order-book/retention/artifacts/{semanticIdentity}/reusable")
    boolean isReusable(@PathVariable String semanticIdentity);

    @PostMapping("/internal/v1/order-book/retention/dependencies")
    DependencyResponse protect(@RequestBody DependencyRequest request);

    @DeleteMapping("/internal/v1/order-book/retention/dependencies/{dependencyKey}")
    void release(@PathVariable String dependencyKey, @RequestBody ReleaseRequest request);

    @PostMapping("/internal/v1/order-book/retention/artifacts")
    void promote(@RequestBody PromotionRequest request);

    @PostMapping("/internal/v1/order-book/retention/artifacts/canonical")
    void promoteCanonical(@RequestBody CanonicalPromotionRequest request);

    record DependencyRequest(String dependencyKey, String consumerType, String consumerIdentity,
                             UUID artifactId, UUID sequenceId, String reason) {
    }

    record ReleaseRequest(String reason) {
    }

    record DependencyResponse(UUID dependencyId, String dependencyKey, String state, UUID artifactId,
                              UUID sequenceId) {
    }

    record PromotionRequest(OrderBookReplayClient.ReplayResponse replay,
                            String semanticIdentity, String promotionReason) {
    }

    record CanonicalPromotionRequest(java.util.UUID marketId, int depth, java.time.Instant cutoff,
                                     String semanticIdentity, String promotionReason) {
    }
}

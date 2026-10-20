package com.hope.trading.trading_core.market_data.apiClient;

import com.hope.trading.trading_core.config.MarketDataFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "market-data", contextId = "orderBookRetentionClient",
        configuration = MarketDataFeignConfiguration.class)
public interface OrderBookRetentionClient {
    @PostMapping("/internal/v1/order-book/retention/dependencies")
    DependencyResponse protect(@RequestBody DependencyRequest request);

    @DeleteMapping("/internal/v1/order-book/retention/dependencies/{dependencyKey}")
    void release(@PathVariable String dependencyKey, @RequestBody ReleaseRequest request);

    record DependencyRequest(String dependencyKey, String consumerType, String consumerIdentity,
                             UUID artifactId, UUID sequenceId, String reason) {
    }

    record ReleaseRequest(String reason) {
    }

    record DependencyResponse(UUID dependencyId, String dependencyKey, String state, UUID artifactId,
                              UUID sequenceId) {
    }
}

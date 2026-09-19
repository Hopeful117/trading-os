package com.hope.trading.trading_core.risk.infrastructure.client;

import com.hope.trading.trading_core.config.BrokerServiceFeignConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "broker-service", contextId = "brokerTechnicalCapabilitiesClient",
        configuration = BrokerServiceFeignConfiguration.class)
interface BrokerTechnicalCapabilitiesFeignClient {
    @GetMapping("/internal/v1/broker-accounts/{id}/capabilities")
    BrokerTechnicalCapabilities get(@PathVariable UUID id, @RequestParam String instrument);
}

record BrokerTechnicalCapabilities(UUID brokerAccountId, String provider, String instrument, long sourceVersion,
                                   Instant observedAt, List<String> supportedOrderTypes,
                                   List<BigDecimal> supportedLeverageLevels) { }

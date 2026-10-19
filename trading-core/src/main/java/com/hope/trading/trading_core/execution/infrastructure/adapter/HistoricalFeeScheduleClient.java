package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.config.BrokerServiceFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "broker-service", contextId = "historical-fee-schedule-client",
        configuration = BrokerServiceFeignConfiguration.class)
public interface HistoricalFeeScheduleClient {
    @GetMapping("/internal/v1/historical-fees/eligible")
    ResponseEntity<ScheduleResponse> findEligible(
            @RequestParam UUID brokerAccountId,
            @RequestParam String provider,
            @RequestParam String productType,
            @RequestParam String instrument,
            @RequestParam Instant executionTime,
            @RequestParam Instant researchCutoff);

    record ScheduleResponse(
            UUID scheduleId,
            UUID brokerAccountId,
            String provider,
            String instrument,
            String productType,
            Instant effectiveFrom,
            Instant effectiveTo,
            BigDecimal makerRate,
            BigDecimal takerRate,
            String rateUnit,
            String sourceId,
            Instant observedAt,
            Instant knownAt,
            String origin,
            String normalizationVersion,
            String scheduleFingerprint
    ) {
    }
}

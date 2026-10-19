package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.api.dto.HistoricalFeeScheduleApiDto;
import com.hope.trading.broker_service.broker.application.port.HistoricalFeeScheduleReader;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/historical-fees")
public final class HistoricalFeeScheduleController {
    private final HistoricalFeeScheduleReader schedules;

    public HistoricalFeeScheduleController(HistoricalFeeScheduleReader schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/eligible")
    public ResponseEntity<HistoricalFeeScheduleApiDto> findEligible(
            @RequestParam UUID brokerAccountId,
            @RequestParam BrokerProviderId provider,
            @RequestParam String productType,
            @RequestParam String instrument,
            @RequestParam Instant executionTime,
            @RequestParam Instant researchCutoff) {
        return schedules.findEligible(brokerAccountId, provider, productType, instrument,
                        executionTime, researchCutoff)
                .map(schedule -> ResponseEntity.ok(HistoricalFeeScheduleApiDto.from(schedule)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

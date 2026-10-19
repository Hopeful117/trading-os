package com.hope.trading.broker_service.broker.api.dto;

import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HistoricalFeeScheduleApiDto(
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
    public static HistoricalFeeScheduleApiDto from(HistoricalFeeSchedule schedule) {
        return new HistoricalFeeScheduleApiDto(
                schedule.scheduleId(), schedule.brokerAccountId(), schedule.provider().name(),
                schedule.instrument(), schedule.productType(), schedule.effectiveFrom(), schedule.effectiveTo(),
                schedule.makerRate(), schedule.takerRate(), schedule.rateUnit().name(), schedule.sourceId(),
                schedule.observedAt(), schedule.knownAt(), schedule.origin().name(),
                schedule.normalizationVersion(), schedule.scheduleFingerprint());
    }
}

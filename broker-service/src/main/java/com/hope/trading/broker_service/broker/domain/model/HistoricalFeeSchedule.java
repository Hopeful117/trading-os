package com.hope.trading.broker_service.broker.domain.model;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalFeeSchedule(
        UUID scheduleId,
        UUID brokerAccountId,
        BrokerProviderId provider,
        String instrument,
        String productType,
        Instant effectiveFrom,
        Instant effectiveTo,
        BigDecimal makerRate,
        BigDecimal takerRate,
        FeeRateUnit rateUnit,
        String settlementAsset,
        String sourceId,
        Instant observedAt,
        Instant knownAt,
        HistoricalFeeScheduleOrigin origin,
        String normalizationVersion,
        String scheduleFingerprint
) {
    public HistoricalFeeSchedule {
        Objects.requireNonNull(scheduleId, "scheduleId is required");
        Objects.requireNonNull(brokerAccountId, "brokerAccountId is required");
        Objects.requireNonNull(provider, "provider is required");
        instrument = required(instrument, "instrument");
        productType = required(productType, "productType");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("effectiveFrom must precede effectiveTo");
        }
        Objects.requireNonNull(makerRate, "makerRate is required");
        Objects.requireNonNull(takerRate, "takerRate is required");
        Objects.requireNonNull(rateUnit, "rateUnit is required");
        if (makerRate.signum() < 0 || takerRate.signum() < 0) {
            throw new IllegalArgumentException("fee rates cannot be negative");
        }
        settlementAsset = required(settlementAsset, "settlementAsset");
        sourceId = required(sourceId, "sourceId");
        Objects.requireNonNull(observedAt, "observedAt is required");
        Objects.requireNonNull(knownAt, "knownAt is required");
        Objects.requireNonNull(origin, "origin is required");
        normalizationVersion = required(normalizationVersion, "normalizationVersion");
        scheduleFingerprint = required(scheduleFingerprint, "scheduleFingerprint");
        if (knownAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("knownAt cannot precede observedAt");
        }
    }

    public HistoricalFeeSchedule(
            UUID scheduleId,
            UUID brokerAccountId,
            BrokerProviderId provider,
            String instrument,
            String productType,
            Instant effectiveFrom,
            Instant effectiveTo,
            BigDecimal makerRate,
            BigDecimal takerRate,
            String settlementAsset,
            String sourceId,
            Instant observedAt,
            Instant knownAt,
            HistoricalFeeScheduleOrigin origin,
            String normalizationVersion,
            String scheduleFingerprint
    ) {
        this(scheduleId, brokerAccountId, provider, instrument, productType, effectiveFrom, effectiveTo,
                makerRate, takerRate, FeeRateUnit.DECIMAL_FRACTION, settlementAsset, sourceId, observedAt, knownAt,
                origin, normalizationVersion, scheduleFingerprint);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}

package com.hope.trading.broker_service.broker.infrastructure.persistence;

import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleOrigin;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "historical_fee_schedule")
class HistoricalFeeScheduleEntity {
    @Id
    @Column(name = "schedule_id")
    UUID scheduleId;
    @Column(name = "broker_account_id", nullable = false)
    UUID brokerAccountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    BrokerProviderId provider;
    @Column(nullable = false, length = 255)
    String instrument;
    @Column(name = "product_type", nullable = false, length = 40)
    String productType;
    @Column(name = "effective_from", nullable = false)
    Instant effectiveFrom;
    @Column(name = "effective_to")
    Instant effectiveTo;
    @Column(name = "maker_rate", nullable = false, precision = 38, scale = 18)
    BigDecimal makerRate;
    @Column(name = "taker_rate", nullable = false, precision = 38, scale = 18)
    BigDecimal takerRate;
    @Column(name = "settlement_asset", nullable = false, length = 32)
    String settlementAsset;
    @Column(name = "source_id", nullable = false, length = 255)
    String sourceId;
    @Column(name = "observed_at", nullable = false)
    Instant observedAt;
    @Column(name = "known_at", nullable = false)
    Instant knownAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    HistoricalFeeScheduleOrigin origin;
    @Column(name = "normalization_version", nullable = false, length = 64)
    String normalizationVersion;
    @Column(name = "schedule_fingerprint", nullable = false, unique = true, length = 64)
    String scheduleFingerprint;

    protected HistoricalFeeScheduleEntity() {
    }

    static HistoricalFeeScheduleEntity from(HistoricalFeeSchedule value) {
        HistoricalFeeScheduleEntity entity = new HistoricalFeeScheduleEntity();
        entity.scheduleId = value.scheduleId();
        entity.brokerAccountId = value.brokerAccountId();
        entity.provider = value.provider();
        entity.instrument = value.instrument();
        entity.productType = value.productType();
        entity.effectiveFrom = value.effectiveFrom();
        entity.effectiveTo = value.effectiveTo();
        entity.makerRate = value.makerRate();
        entity.takerRate = value.takerRate();
        entity.settlementAsset = value.settlementAsset();
        entity.sourceId = value.sourceId();
        entity.observedAt = value.observedAt();
        entity.knownAt = value.knownAt();
        entity.origin = value.origin();
        entity.normalizationVersion = value.normalizationVersion();
        entity.scheduleFingerprint = value.scheduleFingerprint();
        return entity;
    }

    HistoricalFeeSchedule toDomain() {
        return new HistoricalFeeSchedule(scheduleId, brokerAccountId, provider, instrument, productType, effectiveFrom,
                effectiveTo, makerRate, takerRate, settlementAsset, sourceId, observedAt, knownAt, origin,
                normalizationVersion, scheduleFingerprint);
    }
}

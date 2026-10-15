package com.hope.trading.market_data.adapter.persistence;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.OhlcInterval;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "historical_ohlc_evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class JpaHistoricalOhlcEvidenceEntity {
    @Id
    @Column(name = "observation_id")
    UUID observationId;
    @Column(name = "market_id", nullable = false)
    UUID marketId;
    @Enumerated(EnumType.STRING)
    @Column(name = "interval_name", nullable = false, length = 32)
    OhlcInterval interval;
    @Column(name = "open_time", nullable = false)
    Instant openTime;
    @Column(name = "close_time", nullable = false)
    Instant closeTime;
    @Column(nullable = false, precision = 38, scale = 18)
    BigDecimal open;
    @Column(nullable = false, precision = 38, scale = 18)
    BigDecimal high;
    @Column(nullable = false, precision = 38, scale = 18)
    BigDecimal low;
    @Column(nullable = false, precision = 38, scale = 18)
    BigDecimal close;
    @Column(precision = 38, scale = 18)
    BigDecimal volume;
    @Column(precision = 38, scale = 18)
    BigDecimal vwap;
    @Column(name = "trade_count")
    Integer tradeCount;
    @Column(nullable = false)
    boolean closed;
    @Column(nullable = false)
    boolean synthetic;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    MarketProvider provider;
    @Column(name = "provider_symbol")
    String providerSymbol;
    @Column(name = "source_id", nullable = false)
    String sourceId;
    @Column(name = "source_updated_at")
    Instant sourceUpdatedAt;
    @Column(name = "fetched_at", nullable = false)
    Instant fetchedAt;
    @Column(name = "captured_at", nullable = false)
    Instant capturedAt;
    @Column(name = "known_at", nullable = false)
    Instant knownAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    HistoricalOhlcEvidenceOrigin origin;
    @Column(name = "normalization_version", nullable = false, length = 64)
    String normalizationVersion;
    @Column(name = "observation_fingerprint", nullable = false, unique = true, length = 64)
    String observationFingerprint;

    static JpaHistoricalOhlcEvidenceEntity from(HistoricalOhlcEvidence value) {
        JpaHistoricalOhlcEvidenceEntity entity = new JpaHistoricalOhlcEvidenceEntity();
        entity.observationId = value.observationId();
        entity.marketId = value.marketId();
        entity.interval = value.interval();
        entity.openTime = value.openTime();
        entity.closeTime = value.closeTime();
        entity.open = value.open();
        entity.high = value.high();
        entity.low = value.low();
        entity.close = value.close();
        entity.volume = value.volume();
        entity.vwap = value.vwap();
        entity.tradeCount = value.tradeCount();
        entity.closed = value.closed();
        entity.synthetic = value.synthetic();
        entity.provider = value.provider();
        entity.providerSymbol = value.providerSymbol();
        entity.sourceId = value.sourceId();
        entity.sourceUpdatedAt = value.sourceUpdatedAt();
        entity.fetchedAt = value.fetchedAt();
        entity.capturedAt = value.capturedAt();
        entity.knownAt = value.knownAt();
        entity.origin = value.origin();
        entity.normalizationVersion = value.normalizationVersion();
        entity.observationFingerprint = value.observationFingerprint();
        return entity;
    }

    HistoricalOhlcEvidence toDomain() {
        return new HistoricalOhlcEvidence(observationId, marketId, interval, openTime, closeTime,
                open, high, low, close, volume, vwap, tradeCount, closed, synthetic, provider,
                providerSymbol, sourceId, sourceUpdatedAt, fetchedAt, capturedAt, knownAt,
                origin, normalizationVersion, observationFingerprint);
    }
}

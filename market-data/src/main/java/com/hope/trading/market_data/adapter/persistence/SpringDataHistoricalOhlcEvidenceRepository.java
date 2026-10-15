package com.hope.trading.market_data.adapter.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataHistoricalOhlcEvidenceRepository
        extends JpaRepository<JpaHistoricalOhlcEvidenceEntity, UUID>,
        HistoricalOhlcEvidenceInsertRepository {

    Optional<JpaHistoricalOhlcEvidenceEntity> findByObservationFingerprint(String observationFingerprint);

    @Query(value = """
            SELECT observation_id, market_id, interval_name, open_time, close_time,
                   open, high, low, close, volume, vwap, trade_count, closed, synthetic,
                   provider, provider_symbol, source_id, source_updated_at, fetched_at,
                   captured_at, known_at, origin, normalization_version, observation_fingerprint
            FROM (
                SELECT e.*, ROW_NUMBER() OVER (
                    PARTITION BY market_id, interval_name, open_time
                    ORDER BY known_at DESC, captured_at DESC, observation_id DESC
                ) AS version_rank
                FROM historical_ohlc_evidence e
                WHERE market_id = :marketId
                  AND interval_name = :interval
                  AND closed = true
                   AND synthetic = false
                   AND origin = 'CAPTURED'
                   AND close_time <= :asOf
                  AND known_at <= :asOf
            ) eligible
            WHERE version_rank = 1
            ORDER BY open_time DESC
            """, nativeQuery = true)
    List<JpaHistoricalOhlcEvidenceEntity> findStrictObserved(
            @Param("marketId") UUID marketId,
            @Param("interval") String interval,
            @Param("asOf") Instant asOf,
            Pageable pageable
    );
}

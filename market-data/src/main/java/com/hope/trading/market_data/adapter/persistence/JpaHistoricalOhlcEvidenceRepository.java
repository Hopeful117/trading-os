package com.hope.trading.market_data.adapter.persistence;

import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy;
import com.hope.trading.market_data.model.HistoricalOhlcSeries;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.HistoricalOhlcRange;
import com.hope.trading.market_data.service.HistoricalOhlcRangeCalculator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaHistoricalOhlcEvidenceRepository {
    private static final int MAX_LIMIT = 720;
    private final SpringDataHistoricalOhlcEvidenceRepository repository;

    public JpaHistoricalOhlcEvidenceRepository(SpringDataHistoricalOhlcEvidenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<HistoricalOhlcEvidence> append(HistoricalOhlcEvidence evidence) {
        repository.insertIfAbsent(evidence);
        return repository.findByObservationFingerprint(evidence.observationFingerprint())
                .map(JpaHistoricalOhlcEvidenceEntity::toDomain);
    }

    @Transactional(readOnly = true)
    public HistoricalOhlcSeries findAsOf(
            UUID marketId,
            OhlcInterval interval,
            Instant asOf,
            int limit,
            HistoricalOhlcEvidencePolicy policy
    ) {
        if (policy != HistoricalOhlcEvidencePolicy.STRICT_OBSERVED) {
            throw new IllegalArgumentException("Unsupported historical OHLC evidence policy: " + policy);
        }
        if (marketId == null || interval == null || asOf == null) {
            throw new IllegalArgumentException("marketId, interval and asOf are required");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Historical OHLC limit must be between 1 and " + MAX_LIMIT);
        }
        List<HistoricalOhlcEvidence> observations = repository.findStrictObserved(
                        marketId, interval.name(), asOf, PageRequest.of(0, limit)).stream()
                .map(JpaHistoricalOhlcEvidenceEntity::toDomain)
                .sorted(java.util.Comparator.comparing(HistoricalOhlcEvidence::openTime))
                .toList();
        return new HistoricalOhlcSeries(
                marketId,
                interval,
                asOf,
                observations,
                observations.isEmpty()
                        ? HistoricalOhlcSeries.HistoricalOhlcSeriesStatus.INCOMPLETE
                        : HistoricalOhlcSeries.HistoricalOhlcSeriesStatus.AVAILABLE,
                observations.isEmpty() ? "No strict observed evidence is available at the requested cutoff" : null
        );
    }

    @Transactional(readOnly = true)
    public HistoricalOhlcRange findRange(
            UUID marketId,
            OhlcInterval interval,
            java.time.Instant fromExclusive,
            java.time.Instant toInclusive,
            HistoricalOhlcEvidencePolicy policy
    ) {
        HistoricalOhlcRangeCalculator.validateRequest(
                marketId, interval, fromExclusive, toInclusive, policy);
        List<HistoricalOhlcEvidence> candidates = repository.findCapturedRange(
                        marketId, interval.name(), fromExclusive, toInclusive).stream()
                .map(JpaHistoricalOhlcEvidenceEntity::toDomain)
                .toList();
        return HistoricalOhlcRangeCalculator.calculate(
                marketId, interval, fromExclusive, toInclusive, policy, candidates);
    }
}

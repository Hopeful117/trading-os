package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.outcome.OutcomeFutureObservation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

@Component
public class HistoricalOhlcFutureEvidenceSource {
    private static final String INTERVAL = "FIFTEEN_MINUTES";
    private static final String POLICY = "CAPTURED";
    private static final String NORMALIZATION_VERSION = "historical-ohlc-v1";
    private final HistoricalOhlcClient client;

    public HistoricalOhlcFutureEvidenceSource(HistoricalOhlcClient client) {
        this.client = client;
    }

    public HistoricalOhlcRangeSnapshot find(UUID marketId, Instant fromExclusive, Instant toInclusive) {
        HistoricalOhlcClient.HistoricalOhlcRangeResponse response = client.findHistoricalOhlcRange(
                marketId, INTERVAL, fromExclusive, toInclusive, POLICY);
        if (response == null || !Objects.equals(response.marketId(), marketId)
                || !Objects.equals(response.interval(), INTERVAL)
                || !Objects.equals(response.fromExclusive(), fromExclusive)
                || !Objects.equals(response.toInclusive(), toInclusive)
                || !Objects.equals(response.evidencePolicy(), POLICY)) {
            throw new IllegalStateException("Historical future range identity does not match request");
        }
        List<OutcomeFutureObservation> observations = response.observations().stream()
                .map(value -> map(value, marketId, fromExclusive, toInclusive))
                .sorted(java.util.Comparator.comparing(OutcomeFutureObservation::openTime))
                .toList();
        List<String> observedNormalizationVersions = response.observations().stream()
                .map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::normalizationVersion)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(TreeSet::new), List::copyOf));
        if (!observedNormalizationVersions.equals(new TreeSet<>(response.normalizationVersions()).stream().toList())) {
            throw new IllegalStateException("Historical future normalization provenance does not match observations");
        }
        return new HistoricalOhlcRangeSnapshot(
                response.status(), response.reason(), response.expectedObservationCount(),
                response.observedObservationCount(), response.gaps().stream()
                        .map(gap -> gap.openTime() + ".." + gap.closeTime()).toList(),
                response.evidenceFingerprint(), response.normalizationVersions(), observations);
    }

    private OutcomeFutureObservation map(
            HistoricalOhlcClient.HistoricalOhlcEvidenceResponse value,
            UUID marketId,
            Instant fromExclusive,
            Instant toInclusive
    ) {
        if (!Objects.equals(value.marketId(), marketId)
                || !INTERVAL.equals(value.interval())
                || value.openTime() == null || value.closeTime() == null
                || value.openTime().isBefore(fromExclusive)
                || value.closeTime().isAfter(toInclusive)
                || !value.closed() || value.synthetic()
                || !POLICY.equals(value.origin())
                || !NORMALIZATION_VERSION.equals(value.normalizationVersion())
                || value.observationFingerprint() == null) {
            throw new IllegalStateException("Historical future evidence violates bounded range contract");
        }
        return new OutcomeFutureObservation(
                value.openTime(), value.closeTime(), value.high(), value.low(), value.close(),
                value.observationFingerprint());
    }

    public record HistoricalOhlcRangeSnapshot(
            String status,
            String reason,
            int expectedObservationCount,
            int observedObservationCount,
            List<String> gaps,
            String evidenceFingerprint,
            List<String> normalizationVersions,
            List<OutcomeFutureObservation> observations
    ) {
        public HistoricalOhlcRangeSnapshot {
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
            normalizationVersions = normalizationVersions == null ? List.of() : List.copyOf(normalizationVersions);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }
}

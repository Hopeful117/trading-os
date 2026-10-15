package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class HistoricalOhlcEvidenceSource implements OhlcEvidenceSource {
    private static final String SUPPORTED_NORMALIZATION_VERSION = "historical-ohlc-v1";
    private final HistoricalOhlcClient client;

    public HistoricalOhlcEvidenceSource(HistoricalOhlcClient client) {
        this.client = client;
    }

    @Override
    public OhlcEvidenceBatch acquire(UUID marketId, String interval, int limit, Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("Strict observed OHLC requires an evaluation cutoff");
        }
        HistoricalOhlcClient.HistoricalOhlcSeriesResponse series = client.findHistoricalOhlc(
                marketId, interval, cutoff, limit);
        validateSeries(series, marketId, interval, cutoff);
        List<HistoricalOhlcClient.HistoricalOhlcEvidenceResponse> evidence = series.observations();
        validateNormalization(evidence);
        List<OhlcResponse> observations = evidence.stream()
                .map(value -> validateAndMap(value, marketId, interval, cutoff))
                .toList();
        String fingerprint = ArtifactFingerprint.ofInputs(evidence.stream()
                .map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::observationFingerprint)
                .sorted().toList()).value();
        return new OhlcEvidenceBatch(observations, fingerprint,
                SUPPORTED_NORMALIZATION_VERSION, evidence.size());
    }

    private void validateSeries(
            HistoricalOhlcClient.HistoricalOhlcSeriesResponse series,
            UUID marketId,
            String interval,
            Instant cutoff
    ) {
        if (series == null) throw new IllegalStateException("Historical OHLC response is missing");
        if (!Objects.equals(series.marketId(), marketId)
                || !Objects.equals(series.interval(), interval)
                || !Objects.equals(series.requestedAsOf(), cutoff)) {
            throw new IllegalStateException("Historical OHLC response identity does not match request");
        }
        if (!"AVAILABLE".equals(series.status()) && !"INCOMPLETE".equals(series.status())) {
            throw new IllegalStateException("Unsupported historical OHLC series status: " + series.status());
        }
    }

    private OhlcResponse validateAndMap(
            HistoricalOhlcClient.HistoricalOhlcEvidenceResponse value,
            UUID marketId,
            String interval,
            Instant cutoff
    ) {
        if (!Objects.equals(value.marketId(), marketId) || !Objects.equals(value.interval(), interval)
                || !value.closed() || value.synthetic() || !"CAPTURED".equals(value.origin())
                || value.closeTime() == null || value.knownAt() == null
                || value.closeTime().isAfter(cutoff) || value.knownAt().isAfter(cutoff)
                || value.sourceUpdatedAt() != null && value.sourceUpdatedAt().isAfter(cutoff)
                || value.sourceId() == null || value.observationFingerprint() == null) {
            throw new IllegalStateException("Historical OHLC evidence violates strict observed contract");
        }
        Instant occurredAt = value.sourceUpdatedAt() == null ? value.closeTime() : value.sourceUpdatedAt();
        Instant fetchedAt = value.knownAt();
        return new OhlcResponse(
                value.marketId(), value.provider(), value.providerSymbol(), value.interval(),
                value.openTime(), value.closeTime(), value.open(), value.high(), value.low(),
                value.close(), value.volume(), value.vwap(), value.tradeCount(), value.closed(),
                occurredAt, value.synthetic(), value.sourceId(), fetchedAt);
    }

    private void validateNormalization(
            List<HistoricalOhlcClient.HistoricalOhlcEvidenceResponse> evidence) {
        if (evidence.stream().anyMatch(value ->
                !SUPPORTED_NORMALIZATION_VERSION.equals(value.normalizationVersion()))) {
            throw new IllegalStateException("Historical OHLC evidence uses an unsupported normalization version");
        }
    }
}

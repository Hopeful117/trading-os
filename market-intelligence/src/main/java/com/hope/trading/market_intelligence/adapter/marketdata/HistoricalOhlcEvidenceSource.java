package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class HistoricalOhlcEvidenceSource implements OhlcEvidenceSource {
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
        StrictObservedOhlcEvidenceValidator.validate(series, marketId, interval, cutoff);
        List<HistoricalOhlcClient.HistoricalOhlcEvidenceResponse> evidence = series.observations();
        List<OhlcResponse> observations = evidence.stream()
                .map(this::map)
                .toList();
        String fingerprint = ArtifactFingerprint.ofInputs(evidence.stream()
                .map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::observationFingerprint)
                .sorted().toList()).value();
        return new OhlcEvidenceBatch(observations, fingerprint,
                StrictObservedOhlcEvidenceValidator.NORMALIZATION_VERSION, evidence.size());
    }

    private OhlcResponse map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse value) {
        Instant occurredAt = value.sourceUpdatedAt() == null ? value.closeTime() : value.sourceUpdatedAt();
        Instant fetchedAt = value.knownAt();
        return new OhlcResponse(
                value.marketId(), value.provider(), value.providerSymbol(), value.interval(),
                value.openTime(), value.closeTime(), value.open(), value.high(), value.low(),
                value.close(), value.volume(), value.vwap(), value.tradeCount(), value.closed(),
                occurredAt, value.synthetic(), value.sourceId(), fetchedAt);
    }

}

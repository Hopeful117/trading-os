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
        return acquire(marketId, interval, limit, cutoff, null, null);
    }

    @Override
    public OhlcEvidenceBatch acquire(UUID marketId, String interval, int limit, Instant cutoff,
                                     String selectionBinding, String role) {
        if (cutoff == null) {
            throw new IllegalArgumentException("Strict observed OHLC requires an evaluation cutoff");
        }
        HistoricalOhlcClient.HistoricalOhlcSeriesResponse series = client.findHistoricalOhlc(
                marketId, interval, cutoff, limit);
        StrictObservedOhlcEvidenceValidator.validate(series, marketId, interval, cutoff);
        List<HistoricalOhlcClient.HistoricalOhlcEvidenceResponse> evidence = series.observations();
        if (selectionBinding != null) {
            validateSelection(evidence, selectionBinding, role);
        }
        List<OhlcResponse> observations = evidence.stream()
                .map(this::map)
                .toList();
        String fingerprint = ArtifactFingerprint.ofInputs(evidence.stream()
                .map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::observationFingerprint)
                .sorted().toList()).value();
        return new OhlcEvidenceBatch(observations, fingerprint,
                StrictObservedOhlcEvidenceValidator.NORMALIZATION_VERSION, evidence.size());
    }

    private void validateSelection(
            List<HistoricalOhlcClient.HistoricalOhlcEvidenceResponse> evidence,
            String selectionBinding,
            String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalStateException("Historical evidence role is required for a bound selection");
        }
        List<String> expected = selectionBinding.lines()
                .filter(value -> value.startsWith(role + "|"))
                .toList();
        List<String> actual = evidence.stream()
                .map(value -> String.join("|", role, value.observationId().toString(),
                        value.observationFingerprint(), value.fetchedAt().toString(),
                        value.capturedAt().toString(), value.knownAt().toString(), value.origin(),
                        value.normalizationVersion()))
                .toList();
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Historical evidence selection does not match preflight for role " + role);
        }
    }

    private OhlcResponse map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse value) {
        Instant occurredAt = value.sourceUpdatedAt() == null ? value.closeTime() : value.sourceUpdatedAt();
        return new OhlcResponse(
                value.marketId(), value.provider(), value.providerSymbol(), value.interval(),
                value.openTime(), value.closeTime(), value.open(), value.high(), value.low(),
                value.close(), value.volume(), value.vwap(), value.tradeCount(), value.closed(),
                occurredAt, value.synthetic(), value.sourceId(), value.fetchedAt(),
                value.observationId(), value.capturedAt(), value.knownAt(),
                value.observationFingerprint(), value.normalizationVersion(), value.origin());
    }

}

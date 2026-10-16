package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.outcome.OutcomeReferenceEvidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class HistoricalOhlcReferenceEvidenceSource {
    private static final String NORMALIZATION_VERSION = "historical-ohlc-v1";
    private final HistoricalOhlcClient client;

    public HistoricalOhlcReferenceEvidenceSource(HistoricalOhlcClient client) {
        this.client = client;
    }

    public Optional<OutcomeReferenceEvidence> findLatest(UUID marketId, Instant cutoff) {
        HistoricalOhlcClient.HistoricalOhlcSeriesResponse response = client.findHistoricalOhlc(
                marketId, "FIFTEEN_MINUTES", cutoff, 200);
        if (response == null || !Objects.equals(response.marketId(), marketId)
                || !Objects.equals(response.requestedAsOf(), cutoff)) {
            throw new IllegalStateException("Historical reference response identity does not match request");
        }
        return response.observations().stream()
                .filter(value -> Objects.equals(value.marketId(), marketId))
                .filter(value -> "FIFTEEN_MINUTES".equals(value.interval()))
                .filter(value -> value.closed() && !value.synthetic())
                .filter(value -> "CAPTURED".equals(value.origin()))
                .filter(value -> value.closeTime() != null && !value.closeTime().isAfter(cutoff))
                .filter(value -> value.knownAt() != null && !value.knownAt().isAfter(cutoff))
                .filter(value -> value.close() != null && value.close().signum() > 0)
                .filter(value -> value.observationFingerprint() != null)
                .filter(value -> NORMALIZATION_VERSION.equals(value.normalizationVersion()))
                .max(Comparator.comparing(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::closeTime))
                .map(value -> new OutcomeReferenceEvidence(
                        value.close(), value.closeTime(), value.knownAt(),
                        value.observationFingerprint(), value.normalizationVersion()));
    }
}

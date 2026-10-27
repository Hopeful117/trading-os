package com.hope.trading.market_intelligence.adapter.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StrictObservedOhlcEvidenceValidator {
    public static final String NORMALIZATION_VERSION = "historical-ohlc-v1";

    private StrictObservedOhlcEvidenceValidator() { }

    public static void validate(
            HistoricalOhlcClient.HistoricalOhlcSeriesResponse series,
            UUID marketId,
            String interval,
            Instant cutoff
    ) {
        if (series == null) throw invalid("Historical OHLC response is missing");
        if (!Objects.equals(series.marketId(), marketId)
                || !Objects.equals(series.interval(), interval)
                || !Objects.equals(series.requestedAsOf(), cutoff)) {
            throw invalid("Historical OHLC response identity does not match request");
        }
        if (!"AVAILABLE".equals(series.status()) && !"INCOMPLETE".equals(series.status())) {
            throw invalid("Unsupported historical OHLC series status: " + series.status());
        }
        validateNormalization(series.observations());
        for (HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence : series.observations()) {
            if (!Objects.equals(evidence.marketId(), marketId)
                    || !Objects.equals(evidence.interval(), interval)
                    || !evidence.closed()
                    || evidence.synthetic()
                    || !"CAPTURED".equals(evidence.origin())
                    || evidence.closeTime() == null
                    || evidence.knownAt() == null
                    || evidence.closeTime().isAfter(cutoff)
                    || evidence.knownAt().isAfter(cutoff)
                    || evidence.sourceUpdatedAt() != null && evidence.sourceUpdatedAt().isAfter(cutoff)
                    || evidence.sourceId() == null
                    || evidence.observationFingerprint() == null) {
                throw invalid("Historical OHLC evidence violates strict observed contract");
            }
        }
    }

    private static void validateNormalization(
            List<HistoricalOhlcClient.HistoricalOhlcEvidenceResponse> evidence) {
        if (evidence.stream().anyMatch(value ->
                !NORMALIZATION_VERSION.equals(value.normalizationVersion()))) {
            throw invalid("Historical OHLC evidence uses an unsupported normalization version");
        }
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(message);
    }

    public static final class ValidationException extends IllegalStateException {
        private ValidationException(String message) {
            super(message);
        }
    }
}

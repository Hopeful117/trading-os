package com.hope.trading.market_intelligence.domain.outcome;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MarketOutcomeFingerprint {
    private MarketOutcomeFingerprint() {
    }

    public static String fingerprint(
            String definitionVersion,
            UUID marketId,
            Instant evaluationCutoff,
            OutcomeHorizon horizon,
            String referenceObservationFingerprint,
            String futureEvidenceFingerprint,
            ObservedMarketOutcomeStatus status,
            BigDecimal referencePrice,
            BigDecimal finalPrice,
            BigDecimal futureHigh,
            BigDecimal futureLow,
            BigDecimal forwardReturn,
            BigDecimal futureHighReturn,
            BigDecimal futureLowReturn,
            BigDecimal futureRange,
            String referenceNormalizationVersion,
            List<String> futureNormalizationVersions
    ) {
        return ArtifactFingerprint.ofInputs(List.of(
                "definition=" + definitionVersion,
                "market=" + marketId,
                "cutoff=" + evaluationCutoff,
                "horizon=" + horizon.value(),
                "reference=" + value(referenceObservationFingerprint),
                "future=" + value(futureEvidenceFingerprint),
                "referenceNormalization=" + value(referenceNormalizationVersion),
                "futureNormalizations=" + value(futureNormalizationVersions),
                "status=" + status,
                "referencePrice=" + value(referencePrice),
                "finalPrice=" + value(finalPrice),
                "futureHigh=" + value(futureHigh),
                "futureLow=" + value(futureLow),
                "forwardReturn=" + value(forwardReturn),
                "futureHighReturn=" + value(futureHighReturn),
                "futureLowReturn=" + value(futureLowReturn),
                "futureRange=" + value(futureRange)
        )).value();
    }

    private static String value(Object value) {
        return value == null ? "null" : value.toString();
    }
}

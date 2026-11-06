package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureAvailability;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureSourceReference;

import java.time.Instant;
import java.util.List;

final class HistoricalStructureArtifactValidator {
    private HistoricalStructureArtifactValidator() { }

    static void requireAvailable(
            List<ProducedArtifact> artifacts,
            Instant cutoff
    ) {
        if (artifacts.isEmpty()) {
            throw new IllegalStateException("Historical reconstruction requires market-structure-v2");
        }
        artifacts.forEach(value -> {
            if (!ProductionArtifactTypes.MARKET_STRUCTURE.equals(value.type())
                    || !ProductionArtifactTypes.MARKET_STRUCTURE_V2.equals(value.version())
                    || !(value.artifact().content() instanceof MarketStructureArtifactContent content)) {
                throw new IllegalStateException("Historical reconstruction has an invalid market structure artifact");
            }
            if (content.result().availability() != MarketStructureAvailability.AVAILABLE
                    || content.cutOffAt().isAfter(cutoff)) {
                throw new IllegalStateException("Historical reconstruction has unavailable market structure evidence");
            }
            boolean[] hasReference = {false};
            content.result().all().forEach(swing -> swing.evidenceSources().forEach(reference -> {
                hasReference[0] = true;
                requireComplete(reference, cutoff);
            }));
            if (!hasReference[0]) {
                throw new IllegalStateException(
                        "Historical market structure has no auditable OHLC evidence references");
            }
        });
    }

    private static void requireComplete(MarketStructureSourceReference reference, Instant cutoff) {
        if (reference.observationId() == null
                || blank(reference.observationFingerprint())
                || blank(reference.sourceId())
                || reference.sourceOccurredAt() == null
                || reference.fetchedAt() == null
                || reference.capturedAt() == null
                 || reference.knownAt() == null
                 || reference.knownAt().isAfter(cutoff)
                 || blank(reference.normalizationVersion())
                 || !"CAPTURED".equals(reference.origin())) {
            throw new IllegalStateException("Historical market structure evidence provenance is incomplete");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

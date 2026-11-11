package com.hope.trading.market_intelligence.application.research.histdata;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Validates the provider-neutral source contract before ML-01 consumes it. */
public final class HistoricalClosureEvidenceAdapter {
    private static final Set<String> SUPPORTED_CLOSURE_TYPES = Set.of(
            "SCHEDULED", "SCHEDULED_RECURRING", "HOLIDAY", "EXCEPTIONAL");

    private HistoricalClosureEvidenceAdapter() {
    }

    public static Ml01HistDataPreparation.ClosureEvidence toMl01(
            HistoricalClosureEvidenceSource.Evidence evidence,
            Set<UUID> marketIds,
            Instant startCutoffInclusive,
            Instant endCutoffExclusive) {
        if (evidence == null || evidence.evidenceId() == null || evidence.marketId() == null
                || !marketIds.contains(evidence.marketId())
                || evidence.canonicalSymbol() == null || evidence.canonicalSymbol().isBlank()
                || !"H1".equals(evidence.interval()) || !"HISTDATA".equals(evidence.venue())
                || evidence.closureType() == null || evidence.closureType().isBlank()
                || !SUPPORTED_CLOSURE_TYPES.contains(evidence.closureType().toUpperCase())
                || evidence.reason() == null || evidence.reason().isBlank()
                || evidence.closureStart() == null || evidence.closureEnd() == null
                || evidence.sourceId() == null || evidence.sourceId().isBlank()
                || evidence.sourceVersion() == null || evidence.sourceVersion().isBlank()
                || evidence.knownAt() == null || evidence.effectiveFrom() == null
                || evidence.effectiveTo() == null || evidence.fingerprint() == null
                || evidence.fingerprint().isBlank()) {
            throw new IllegalArgumentException("closure evidence does not satisfy the ML-01 source contract");
        }
        if (evidence.closureStart().isBefore(startCutoffInclusive)
                 || !evidence.closureStart().isBefore(endCutoffExclusive)
                 || evidence.closureEnd().isAfter(endCutoffExclusive)
                 || evidence.knownAt().isAfter(endCutoffExclusive)
                || !evidence.closureStart().isBefore(evidence.closureEnd())
                || !evidence.effectiveFrom().isBefore(evidence.effectiveTo())
                || evidence.closureStart().isBefore(evidence.effectiveFrom())
                || evidence.closureEnd().isAfter(evidence.effectiveTo())) {
            throw new IllegalArgumentException("closure evidence is outside the export scope");
        }
        return new Ml01HistDataPreparation.ClosureEvidence(
                evidence.evidenceId(), evidence.marketId(), evidence.canonicalSymbol(), evidence.interval(), evidence.venue(),
                evidence.closureType(), evidence.reason(),
                evidence.closureStart(), evidence.closureEnd(), evidence.knownAt(),
                evidence.effectiveFrom(), evidence.effectiveTo(), evidence.sourceId(),
                evidence.sourceVersion(), evidence.fingerprint());
    }
}

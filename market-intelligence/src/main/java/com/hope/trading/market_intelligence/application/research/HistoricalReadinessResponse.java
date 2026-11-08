package com.hope.trading.market_intelligence.application.research;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record HistoricalReadinessResponse(
        HistoricalReadinessVerdict verdict,
        Set<UUID> marketIds,
        Instant requestedCutoffStart,
        Instant requestedCutoffEnd,
        Instant effectiveCutoffStart,
        Instant effectiveCutoffEnd,
        long availableEvidenceCount,
        long featureSnapshotCount,
        long eligibleOutcomeCount,
        long postCutoffEvidenceCount,
        Instant earliestEligibleEvidence,
        Instant latestEligibleEvidence,
        Map<UUID, Long> evidenceByMarket,
        Map<String, Long> outcomesByHorizonAndStatus,
        String featureProjectionVersion,
        String featureProvenanceStatus,
        String outcomeMaturityStatus,
        List<String> blockers,
        Instant diagnosticAt,
        String correlationId
) {
}

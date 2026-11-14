package com.hope.trading.market_intelligence.domain.scan;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable source provenance of an ActiveScan launched from a passive candidate.
 * Captured once at creation from server-side revalidation; never mutated and never
 * written back to the passive source records.
 */
public record PassiveCandidateProvenance(
        UUID opportunityId,
        long opportunityVersion,
        UUID strategyMatchId,
        List<UUID> observationIds,
        UUID thesisId,
        long thesisVersion,
        UUID passiveScanRunId,
        UUID passiveScanMarketId,
        UUID passiveAnalysisExecutionId,
        UUID marketId,
        String provider,
        String productType,
        Instant opportunityEvaluatedAt,
        Instant thesisSourceCutoff,
        Instant validUntil,
        Instant validatedAt,
        List<PassiveEvidenceAssessment> evidenceAssessment
) {
    public PassiveCandidateProvenance {
        Objects.requireNonNull(opportunityId, "opportunityId");
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(validatedAt, "validatedAt");
        observationIds = observationIds == null ? List.of() : List.copyOf(observationIds);
        evidenceAssessment = evidenceAssessment == null ? List.of() : List.copyOf(evidenceAssessment);
    }

    public PassiveCandidateReference reference() {
        return new PassiveCandidateReference(opportunityId, opportunityVersion);
    }
}

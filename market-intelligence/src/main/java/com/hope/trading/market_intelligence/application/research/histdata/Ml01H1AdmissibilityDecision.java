package com.hope.trading.market_intelligence.application.research.histdata;

import java.time.LocalDateTime;
import java.util.List;

public record Ml01H1AdmissibilityDecision(
        String profileName,
        String profileVersion,
        Ml01H1ResearchQualityState state,
        List<Ml01H1RejectionReason> rejectionReasons,
        String primaryRejectionReason,
        String instrument,
        String sourceId,
        String sourceFingerprint,
        LocalDateTime sourceOpenTime,
        java.time.Instant sourceCloseTime,
        int expectedM1Count,
        int uniqueValidM1Count,
        List<LocalDateTime> missingMinutePositions,
        boolean firstMinutePresent,
        boolean lastMinutePresent,
        int maximumConsecutiveMissingMinuteRun,
        boolean malformedObservation,
        boolean invalidOhlc,
        boolean conflictingDuplicate,
        boolean chronologyAnomaly,
        HistDataOfflinePipeline.AggregationState strictState,
        List<String> strictContinuityClassifications
) {
    public Ml01H1AdmissibilityDecision {
        if (profileName == null || profileName.isBlank() || profileVersion == null || profileVersion.isBlank()
                || state == null || rejectionReasons == null || instrument == null || sourceId == null
                || sourceOpenTime == null || sourceCloseTime == null || strictState == null
                || strictContinuityClassifications == null) {
            throw new IllegalArgumentException("complete ML-01 admissibility decision is required");
        }
        rejectionReasons = rejectionReasons.stream().distinct().sorted().toList();
        missingMinutePositions = List.copyOf(missingMinutePositions == null ? List.of() : missingMinutePositions);
        strictContinuityClassifications = strictContinuityClassifications.stream().sorted().toList();
        if ((state == Ml01H1ResearchQualityState.FULL_OBSERVED
                || state == Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY)
                && !rejectionReasons.isEmpty()) {
            throw new IllegalArgumentException("eligible decisions cannot contain rejection reasons");
        }
        if (state == Ml01H1ResearchQualityState.UNKNOWN_QUALITY
                && !rejectionReasons.contains(Ml01H1RejectionReason.UNKNOWN_PROVENANCE)
                && !rejectionReasons.contains(Ml01H1RejectionReason.CONTRADICTORY_EVIDENCE)) {
            throw new IllegalArgumentException("unknown quality requires an unknown-evidence reason");
        }
    }

    public boolean eligible() {
        return state == Ml01H1ResearchQualityState.FULL_OBSERVED
                || state == Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY;
    }
}

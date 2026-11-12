package com.hope.trading.market_intelligence.application.research.histdata;

import java.util.ArrayList;
import java.util.List;

public final class Ml01DukascopyH1AdmissibilityEvaluator {
    private final Ml01DukascopyH1AdmissibilityProfile profile;

    public Ml01DukascopyH1AdmissibilityEvaluator() {
        this(Ml01DukascopyH1AdmissibilityProfile.V1);
    }

    public Ml01DukascopyH1AdmissibilityEvaluator(Ml01DukascopyH1AdmissibilityProfile profile) {
        this.profile = profile;
    }

    public Ml01DukascopyH1AdmissibilityDecision evaluate(DukascopyH1Bucket bucket) {
        if (bucket == null) throw new IllegalArgumentException("H1 bucket is required");
        List<Ml01DukascopyH1RejectionReason> reasons = new ArrayList<>();
        if (bucket.sourceFingerprint().isBlank()) reasons.add(Ml01DukascopyH1RejectionReason.UNKNOWN_PROVENANCE);
        if (bucket.malformedObservation()) reasons.add(Ml01DukascopyH1RejectionReason.MALFORMED_OBSERVATION);
        if (bucket.invalidOhlc() || !bucket.hasOhlc() && bucket.uniqueValidM1Count() > 0) {
            reasons.add(Ml01DukascopyH1RejectionReason.INVALID_OHLC);
        }
        if (bucket.conflictingDuplicate()) reasons.add(Ml01DukascopyH1RejectionReason.CONFLICTING_DUPLICATE);
        if (bucket.chronologyAnomaly()) reasons.add(Ml01DukascopyH1RejectionReason.CHRONOLOGY_ANOMALY);
        if (bucket.crossFileOverlap()) reasons.add(Ml01DukascopyH1RejectionReason.CROSS_FILE_OVERLAP);
        if (bucket.unknownSourceIntegrity()) reasons.add(Ml01DukascopyH1RejectionReason.UNKNOWN_SOURCE_INTEGRITY);
        if (profile.firstMinuteRequired() && !bucket.firstMinutePresent()) {
            reasons.add(Ml01DukascopyH1RejectionReason.MISSING_FIRST_MINUTE);
        }
        if (profile.lastMinuteRequired() && !bucket.lastMinutePresent()) {
            reasons.add(Ml01DukascopyH1RejectionReason.MISSING_LAST_MINUTE);
        }
        if (bucket.uniqueValidM1Count() < profile.minimumValidM1Count()) {
            reasons.add(bucket.uniqueValidM1Count() == 0
                    ? Ml01DukascopyH1RejectionReason.EMPTY_H1_BUCKET
                    : Ml01DukascopyH1RejectionReason.INSUFFICIENT_VALID_M1_COUNT);
        }
        if (reasons.contains(Ml01DukascopyH1RejectionReason.UNKNOWN_PROVENANCE)) {
            return decision(DukascopyH1ResearchQualityState.UNKNOWN_QUALITY, reasons, bucket);
        }
        if (!reasons.isEmpty()) return decision(DukascopyH1ResearchQualityState.REJECTED, reasons, bucket);
        DukascopyH1ResearchQualityState state = bucket.uniqueValidM1Count() == profile.expectedM1Count()
                ? DukascopyH1ResearchQualityState.FULL_OBSERVED
                : DukascopyH1ResearchQualityState.PARTIAL_EXPLORATORY;
        return decision(state, List.of(), bucket);
    }

    private Ml01DukascopyH1AdmissibilityDecision decision(DukascopyH1ResearchQualityState state,
                                                           List<Ml01DukascopyH1RejectionReason> reasons,
                                                           DukascopyH1Bucket bucket) {
        return new Ml01DukascopyH1AdmissibilityDecision(profile.name(), profile.version(), state, reasons, bucket);
    }
}

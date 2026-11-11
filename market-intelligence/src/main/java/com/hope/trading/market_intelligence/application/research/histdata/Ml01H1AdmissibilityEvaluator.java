package com.hope.trading.market_intelligence.application.research.histdata;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class Ml01H1AdmissibilityEvaluator {
    private final Ml01ExploratoryH1AdmissibilityProfile profile;

    public Ml01H1AdmissibilityEvaluator() {
        this(Ml01ExploratoryH1AdmissibilityProfile.V1);
    }

    public Ml01H1AdmissibilityEvaluator(Ml01ExploratoryH1AdmissibilityProfile profile) {
        this.profile = profile;
    }

    public Ml01H1AdmissibilityDecision evaluate(HistDataOfflinePipeline.H1BucketQualityEvidence evidence) {
        if (evidence == null) {
            return unknown(null);
        }
        if (!coherent(evidence)) {
            return decision(evidence, Ml01H1ResearchQualityState.UNKNOWN_QUALITY,
                    List.of(Ml01H1RejectionReason.CONTRADICTORY_EVIDENCE));
        }

        List<Ml01H1RejectionReason> reasons = new ArrayList<>();
        if (evidence.provenanceIncomplete() || evidence.sourceId().isBlank()
                || evidence.sourceFingerprint() == null || evidence.sourceFingerprint().isBlank()
                || evidence.strictContinuityClassifications().isEmpty()) {
            reasons.add(Ml01H1RejectionReason.UNKNOWN_PROVENANCE);
        }
        if (evidence.expectedM1Count() != profile.expectedM1Count()) {
            reasons.add(Ml01H1RejectionReason.UNEXPECTED_M1_COUNT);
        }
        if (evidence.malformedObservation()) reasons.add(Ml01H1RejectionReason.MALFORMED_OBSERVATION);
        if (evidence.conflictingDuplicate()) reasons.add(Ml01H1RejectionReason.CONFLICTING_DUPLICATE);
        if (evidence.invalidOhlc()) reasons.add(Ml01H1RejectionReason.INVALID_OHLC);
        if (evidence.chronologyAnomaly()) reasons.add(Ml01H1RejectionReason.CHRONOLOGY_ANOMALY);
        if (profile.firstMinuteRequired() && !evidence.firstMinutePresent()) {
            reasons.add(Ml01H1RejectionReason.MISSING_FIRST_MINUTE);
        }
        if (profile.lastMinuteRequired() && !evidence.lastMinutePresent()) {
            reasons.add(Ml01H1RejectionReason.MISSING_LAST_MINUTE);
        }
        if (evidence.uniqueValidM1Count() < profile.minimumValidM1Count()) {
            reasons.add(Ml01H1RejectionReason.INSUFFICIENT_VALID_M1_COUNT);
        }
        if (evidence.uniqueValidM1Count() > profile.expectedM1Count()) {
            reasons.add(Ml01H1RejectionReason.UNEXPECTED_M1_COUNT);
        }

        if (reasons.contains(Ml01H1RejectionReason.UNKNOWN_PROVENANCE)) {
            return decision(evidence, Ml01H1ResearchQualityState.UNKNOWN_QUALITY, reasons);
        }
        if (!reasons.isEmpty()) {
            return decision(evidence, Ml01H1ResearchQualityState.REJECTED, reasons);
        }
        Ml01H1ResearchQualityState state = evidence.uniqueValidM1Count() == profile.expectedM1Count()
                ? Ml01H1ResearchQualityState.FULL_OBSERVED
                : Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY;
        return decision(evidence, state, List.of());
    }

    private Ml01H1AdmissibilityDecision unknown(HistDataOfflinePipeline.H1BucketQualityEvidence evidence) {
        if (evidence == null) {
            return new Ml01H1AdmissibilityDecision(profile.name(), profile.version(),
                    Ml01H1ResearchQualityState.UNKNOWN_QUALITY,
                    List.of(Ml01H1RejectionReason.UNKNOWN_PROVENANCE),
                    Ml01H1RejectionReason.UNKNOWN_PROVENANCE.name(), "UNKNOWN", "UNKNOWN", null,
                    LocalDateTime.MIN, null, 0, 0, List.of(), false, false, 0,
                    false, false, false, false,
                    HistDataOfflinePipeline.AggregationState.INVALID, List.of());
        }
        return decision(evidence, Ml01H1ResearchQualityState.UNKNOWN_QUALITY,
                List.of(Ml01H1RejectionReason.UNKNOWN_PROVENANCE));
    }

    private Ml01H1AdmissibilityDecision decision(HistDataOfflinePipeline.H1BucketQualityEvidence evidence,
                                                  Ml01H1ResearchQualityState state,
                                                  List<Ml01H1RejectionReason> reasons) {
        List<Ml01H1RejectionReason> ordered = reasons.stream().distinct().sorted().toList();
        return new Ml01H1AdmissibilityDecision(profile.name(), profile.version(), state, ordered,
                ordered.isEmpty() ? null : ordered.getFirst().name(), evidence.instrument(), evidence.sourceId(),
                evidence.sourceFingerprint(), evidence.sourceOpenTime(), evidence.closeTime(), evidence.expectedM1Count(),
                evidence.uniqueValidM1Count(), evidence.missingMinutePositions(), evidence.firstMinutePresent(),
                evidence.lastMinutePresent(), evidence.maximumConsecutiveMissingMinuteRun(), evidence.malformedObservation(),
                evidence.invalidOhlc(), evidence.conflictingDuplicate(), evidence.chronologyAnomaly(), evidence.strictState(),
                evidence.strictContinuityClassifications());
    }

    private boolean coherent(HistDataOfflinePipeline.H1BucketQualityEvidence evidence) {
        List<LocalDateTime> missing = evidence.missingMinutePositions();
        LocalDateTime start = evidence.sourceOpenTime();
        LocalDateTime end = start.plusMinutes(evidence.expectedM1Count());
        if (!evidence.closeTime().equals(end.toInstant(ZoneOffset.ofHours(-5)))) {
            return false;
        }
        if (missing.size() != evidence.expectedM1Count() - evidence.uniqueValidM1Count()
                || missing.stream().distinct().count() != missing.size()
                || missing.stream().anyMatch(value -> value.isBefore(start) || !value.isBefore(end))) {
            return false;
        }
        boolean firstMissing = missing.contains(start);
        boolean lastMissing = missing.contains(end.minusMinutes(1));
        if (evidence.firstMinutePresent() == firstMissing || evidence.lastMinutePresent() == lastMissing) {
            return false;
        }
        int maximumRun = 0;
        int currentRun = 0;
        for (int minute = 0; minute < evidence.expectedM1Count(); minute++) {
            if (missing.contains(start.plusMinutes(minute))) {
                currentRun++;
                maximumRun = Math.max(maximumRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        if (maximumRun != evidence.maximumConsecutiveMissingMinuteRun()) return false;
        if (evidence.strictContinuityClassifications().stream().anyMatch(value -> value == null || value.isBlank())) {
            return false;
        }
        if (evidence.strictState() == HistDataOfflinePipeline.AggregationState.COMPLETE
                && evidence.uniqueValidM1Count() != evidence.expectedM1Count()) return false;
        if (evidence.strictState() != HistDataOfflinePipeline.AggregationState.COMPLETE
                && evidence.uniqueValidM1Count() == evidence.expectedM1Count()) return false;
        return evidence.strictState() != HistDataOfflinePipeline.AggregationState.INVALID
                || evidence.invalidOhlc();
    }
}
